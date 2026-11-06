package fr.michael.logistics.shipment.infrastructure.messaging;

import fr.michael.logistics.shipment.application.DeadLetter;
import fr.michael.logistics.shipment.application.DeadLetterPosition;
import fr.michael.logistics.shipment.application.ScanEventDeadLetters;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.support.KafkaHeaders;

/**
 * Lit le DLT avec un consommateur à part, sans groupe ({@code assign} et {@code seek}) : aucun offset n'est commité,
 * ni pour ce topic ni pour le groupe de l'écouteur. Un consommateur par appel, fermé à la fin.
 */
class KafkaScanEventDeadLetters implements ScanEventDeadLetters {

    private static final Duration POLL = Duration.ofMillis(200);

    private final Map<String, Object> consumerConfig;
    private final KafkaOperations<String, String> kafka;
    private final String topic;
    private final String replayTopic;
    private final Duration timeout;

    /**
     * {@code consumerConfig} : celle de l'écouteur (serveurs, sécurité), dont le groupe est retiré.
     * {@code timeout} : attente maximale d'une lecture sans progrès, ou d'un accusé du broker.
     */
    KafkaScanEventDeadLetters(Map<String, Object> consumerConfig, KafkaOperations<String, String> kafka,
            String topic, String replayTopic, Duration timeout) {
        Map<String, Object> config = new HashMap<>(consumerConfig);
        config.remove(ConsumerConfig.GROUP_ID_CONFIG);
        config.remove(ConsumerConfig.GROUP_INSTANCE_ID_CONFIG);
        config.remove(ConsumerConfig.CLIENT_ID_CONFIG);
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        // Lire un DLT absent ne doit pas le créer (avec une seule partition, par le broker).
        config.put(ConsumerConfig.ALLOW_AUTO_CREATE_TOPICS_CONFIG, false);
        this.consumerConfig = Map.copyOf(config);
        this.kafka = kafka;
        this.topic = topic;
        this.replayTopic = replayTopic;
        this.timeout = timeout;
    }

    @Override
    public List<DeadLetter> latest(int limit) {
        try (KafkaConsumer<String, String> consumer = newConsumer()) {
            List<TopicPartition> partitions = partitions(consumer);
            if (partitions.isEmpty()) {
                return List.of();
            }
            consumer.assign(partitions);
            Map<TopicPartition, Long> beginnings = consumer.beginningOffsets(partitions);
            Map<TopicPartition, Long> ends = consumer.endOffsets(partitions);
            for (TopicPartition partition : partitions) {
                consumer.seek(partition, Math.max(beginnings.get(partition), ends.get(partition) - limit));
            }
            List<DeadLetter> letters = new ArrayList<>();
            read(consumer, ends, letters::add);
            return letters.stream()
                    .sorted(Comparator.comparing(DeadLetter::timestamp)
                            .thenComparingInt(DeadLetter::partition)
                            .thenComparingLong(DeadLetter::offset)
                            .reversed())
                    .limit(limit)
                    .toList();
        }
    }

    @Override
    public Optional<DeadLetter> find(DeadLetterPosition position) {
        try (KafkaConsumer<String, String> consumer = newConsumer()) {
            TopicPartition partition = new TopicPartition(topic, position.partition());
            if (!partitions(consumer).contains(partition)) {
                return Optional.empty();
            }
            long beginning = consumer.beginningOffsets(List.of(partition)).get(partition);
            long end = consumer.endOffsets(List.of(partition)).get(partition);
            if (position.offset() < beginning || position.offset() >= end) {
                return Optional.empty();
            }
            consumer.assign(List.of(partition));
            consumer.seek(partition, position.offset());
            List<DeadLetter> letters = new ArrayList<>();
            read(consumer, Map.of(partition, position.offset() + 1), letters::add);
            // Vide si l'offset est un trou (marqueur de transaction, compaction) : le premier message lu est plus loin.
            return letters.stream().filter(letter -> letter.offset() == position.offset()).findFirst();
        }
    }

    @Override
    public void forEach(Consumer<DeadLetter> action) {
        try (KafkaConsumer<String, String> consumer = newConsumer()) {
            List<TopicPartition> partitions = partitions(consumer);
            if (partitions.isEmpty()) {
                return;
            }
            Map<TopicPartition, Long> beginnings = consumer.beginningOffsets(partitions);
            Map<TopicPartition, Long> ends = consumer.endOffsets(partitions);
            for (TopicPartition partition : partitions) {
                consumer.assign(List.of(partition));
                consumer.seek(partition, beginnings.get(partition));
                read(consumer, Map.of(partition, ends.get(partition)), action);
            }
        }
    }

    @Override
    public void republish(String key, String value) {
        // Sans en-têtes : ceux du DLT (exception, origine) n'ont pas de sens pour l'écouteur.
        try {
            kafka.send(new ProducerRecord<>(replayTopic, key, value))
                    .get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while replaying a dead letter", e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("Broker did not acknowledge the replay of a dead letter", e);
        }
    }

    /** Messages des partitions assignées, de leur position jusqu'à {@code ends} (exclu). */
    private void read(KafkaConsumer<String, String> consumer, Map<TopicPartition, Long> ends,
            Consumer<DeadLetter> action) {
        long lastProgress = System.nanoTime();
        while (!reached(consumer, ends)) {
            if (System.nanoTime() - lastProgress > timeout.toNanos()) {
                throw new IllegalStateException("Timed out reading " + topic);
            }
            for (ConsumerRecord<String, String> record : consumer.poll(POLL)) {
                lastProgress = System.nanoTime();
                if (record.offset() < ends.get(new TopicPartition(record.topic(), record.partition()))) {
                    action.accept(toDeadLetter(record));
                }
            }
        }
    }

    private static boolean reached(KafkaConsumer<String, String> consumer, Map<TopicPartition, Long> ends) {
        return ends.entrySet().stream().allMatch(end -> consumer.position(end.getKey()) >= end.getValue());
    }

    private List<TopicPartition> partitions(KafkaConsumer<String, String> consumer) {
        return consumer.partitionsFor(topic, timeout).stream()
                .map(info -> new TopicPartition(topic, info.partition()))
                .sorted(Comparator.comparingInt(TopicPartition::partition))
                .toList();
    }

    private KafkaConsumer<String, String> newConsumer() {
        return new KafkaConsumer<>(consumerConfig, new StringDeserializer(), new StringDeserializer());
    }

    private static DeadLetter toDeadLetter(ConsumerRecord<String, String> record) {
        return new DeadLetter(record.partition(), record.offset(), Instant.ofEpochMilli(record.timestamp()),
                record.key(), record.value(),
                header(record, KafkaHeaders.DLT_EXCEPTION_FQCN), header(record, KafkaHeaders.DLT_EXCEPTION_MESSAGE));
    }

    private static String header(ConsumerRecord<?, ?> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
