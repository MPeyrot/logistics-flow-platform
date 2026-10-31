# Usage : source scripts/env.sh
# Fixe JAVA_HOME sur openjdk@21 pour ce shell (le JDK par défaut de la machine est plus récent).
export JAVA_HOME="$(brew --prefix openjdk@21)/libexec/openjdk.jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
