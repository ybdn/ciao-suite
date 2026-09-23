#!/bin/sh
# Vérifie que gradle/wrapper/gradle-wrapper.jar est bien celui publié par Gradle pour la version
# déclarée dans gradle-wrapper.properties. Un jar de wrapper piégé s'exécute dès ./gradlew.
# Remplace gradle/actions/wrapper-validation (exclu de la suite, voir CLAUDE.md).
set -eu

cd "$(dirname "$0")/../.."

version="$(sed -nE 's#^distributionUrl=.*/gradle-(.*)-(bin|all)\.zip$#\1#p' gradle/wrapper/gradle-wrapper.properties)"
if [ -z "$version" ]; then
    echo "::error::Version de Gradle introuvable dans gradle-wrapper.properties" >&2
    exit 1
fi

expected="$(curl -fsSL "https://services.gradle.org/distributions/gradle-${version}-wrapper.jar.sha256")"
actual="$(sha256sum gradle/wrapper/gradle-wrapper.jar | cut -d ' ' -f 1)"

if [ "$expected" != "$actual" ]; then
    echo "::error::gradle-wrapper.jar ne correspond pas à Gradle ${version} (attendu ${expected}, obtenu ${actual})" >&2
    exit 1
fi
echo "gradle-wrapper.jar conforme à Gradle ${version}."
