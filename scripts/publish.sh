#!/bin/bash

# Script para publicar en Maven Central
# Requiere: GPG key configurada y ~/.m2/settings.xml con credenciales

set -e

echo "=== Publicando Obfuscador en Maven Central ==="

# Verificar que no hay cambios sin commitear
if [[ -n $(git status --porcelain) ]]; then
    echo "ERROR: Hay cambios sin commitear. Haz commit primero."
    exit 1
fi

# Verificar que estamos en main
BRANCH=$(git rev-parse --abbrev-ref HEAD)
if [[ "$BRANCH" != "main" ]]; then
    echo "ERROR: Debes estar en la rama main para publicar."
    exit 1
fi

# Obtener versión del pom
VERSION=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout)
echo "Versión a publicar: $VERSION"

# Verificar que no es SNAPSHOT
if [[ "$VERSION" == *"SNAPSHOT"* ]]; then
    echo "ERROR: No se pueden publicar versiones SNAPSHOT en Maven Central."
    echo "Cambia la versión en pom.xml a una versión release (ej: 1.0.0)"
    exit 1
fi

# Ejecutar tests
echo "Ejecutando tests..."
mvn clean verify -Prelease

# Publicar
echo "Publicando en Maven Central..."
mvn central-publishing:publish -Prelease

echo "=== Publicación completada ==="
echo "La librería estará disponible en Maven Central en unos minutos."
echo "https://central.sonatype.com/artifact/com.obfuscador/obfuscador-core"
