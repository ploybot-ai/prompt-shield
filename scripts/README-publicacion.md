# Guía de Publicación en Maven Central

## Prerrequisitos

### 1. Cuenta de Sonatype OSSRH
- Regístrate en https://issues.sonatype.org
- Crea un ticket para reclamar el groupId `com.obfuscador`
- Espera aprobación (puede tardar 1-2 días)

### 2. GPG Key
```bash
# Generar clave GPG
gpg --gen-key

# Listar claves
gpg --list-keys

# Enviar a keyserver público
gpg --keyserver keyserver.ubuntu.com --send-keys TU_KEY_ID
```

### 3. Configurar settings.xml
```bash
cp scripts/settings.xml.template ~/.m2/settings.xml
# Edita con tus credenciales
```

## Publicar Versión

### 1. Cambiar versión a release
```bash
# En pom.xml, cambia:
# <version>1.0.0-SNAPSHOT</version>
# a:
# <version>1.0.0</version>
```

### 2. Hacer commit y tag
```bash
git add -A
git commit -m "release: v1.0.0"
git tag v1.0.0
git push origin main --tags
```

### 3. Ejecutar script de publicación
```bash
chmod +x scripts/publish.sh
./scripts/publish.sh
```

## Consumir la Librería

### Maven
```xml
<dependency>
    <groupId>com.obfuscador</groupId>
    <artifactId>obfuscador-core</artifactId>
    <version>1.0.0</version>
</dependency>

<dependency>
    <groupId>com.obfuscador</groupId>
    <artifactId>obfuscador-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Gradle
```groovy
implementation 'com.obfuscador:obfuscador-core:1.0.0'
implementation 'com.obfuscador:obfuscador-spring-boot-starter:1.0.0'
```

## Troubleshooting

### Error: "Failed to deploy artifacts"
- Verifica credenciales en `~/.m2/settings.xml`
- Asegúrate de tener GPG configurado

### Error: "Repository does not exist"
- Verifica que el groupId está reclamado en Sonatype
- Espera a que se sincronice (puede tardar ~30 min)

### Error: "GPG signing failed"
- Verifica que GPG está instalado: `gpg --version`
- Verifica la passphrase en settings.xml
