import { ChangeDetectionStrategy, Component, computed, signal } from '@angular/core';
import { BLOCKS, Block, BlockId, EDGES, SCAN_STEPS } from './architecture-data';

/** Page `/projet/architecture` : schéma SVG des services, panneau par bloc, parcours d'un scan. */
@Component({
  selector: 'app-project-architecture',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './architecture.scss',
  templateUrl: './architecture.html',
})
export class Architecture {
  protected readonly blocks = BLOCKS;
  protected readonly edges = EDGES;
  protected readonly steps = SCAN_STEPS;

  protected readonly selectedId = signal<BlockId | null>(null);
  /** Indice de l'étape du parcours, `null` hors parcours. */
  protected readonly stepIndex = signal<number | null>(null);

  protected readonly selected = computed<Block | null>(
    () => BLOCKS.find((b) => b.id === this.selectedId()) ?? null,
  );
  protected readonly step = computed(() => {
    const i = this.stepIndex();
    return i === null ? null : SCAN_STEPS[i];
  });
  protected readonly activeEdges = computed(() => new Set(this.step()?.edges ?? []));
  protected readonly activeBlocks = computed(() => new Set<BlockId>(this.step()?.blocks ?? []));
  protected readonly atLastStep = computed(() => this.stepIndex() === SCAN_STEPS.length - 1);

  protected select(id: BlockId): void {
    this.selectedId.set(id);
  }

  protected next(): void {
    const i = this.stepIndex();
    this.stepIndex.set(i === null ? 0 : Math.min(i + 1, SCAN_STEPS.length - 1));
  }

  protected previous(): void {
    const i = this.stepIndex();
    if (i !== null && i > 0) {
      this.stepIndex.set(i - 1);
    }
  }

  protected stop(): void {
    this.stepIndex.set(null);
  }
}
