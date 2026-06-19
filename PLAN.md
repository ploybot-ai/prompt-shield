# Plan: Librería Spring Boot para Ofuscación de Prompts IA

## Objetivo
Crear un Spring Boot Starter que ofusque datos sensibles en prompts de IA usando etiquetas con hash (ej: `<DNI_a1b2c3>`) y permita restaurar los valores originales en las respuestas.

---

## Arquitectura

```
obfuscador/
├── obfuscador-core/                    # Librería core (sin dependencias Spring)
│   ├── src/main/java/com/obfuscador/
│   │   ├── model/
│   │   │   ├── ObfuscationTag.java         # Modelo de etiqueta (tipo, hash, valor original)
│   │   │   ├── SensitiveData.java          # Datos sensibles (tipo, valor, metadatos)
│   │   │   └── ObfuscationConfig.java      # Configuración de la librería
│   │   ├── engine/
│   │   │   └── ObfuscationEngine.java      # Motor principal (ofuscar/desusofuscar)
│   │   ├── registry/
│   │   │   ├── SensitiveDataType.java       # Enum de tipos predefinidos
│   │   │   └── DataTypeRegistry.java       # Registro de tipos personalizables
│   │   ├── hash/
│   │   │   └── HashGenerator.java          # Generador de hash para etiquetas
│   │   ├── storage/
│   │   │   ├── StorageService.java         # Interfaz de almacenamiento
│   │   │   └── InMemoryStorageService.java # Implementación por defecto
│   │   └── exception/
│   │       ├── ObfuscationException.java
│   │       └── TagNotFoundException.java
│   └── src/test/java/com/obfuscador/
│       └── engine/
│           └── ObfuscationEngineTest.java
│
└── obfuscador-spring-boot-starter/     # Starter con auto-configuración
    ├── src/main/java/com/obfuscador/spring/
    │   ├── autoconfigure/
    │   │   ├── ObfuscationAutoConfiguration.java
    │   │   └── ObfuscationProperties.java
    │   ├── annotation/
    │   │   └── Obfuscate.java             # Anotación para parámetros
    │   └── interceptor/
    │       └── ObfuscationInterceptor.java  # Interceptor AOP
    └── src/main/resources/
        └── META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

---

## Funcionalidades Clave

### 1. Formato de Etiquetas
- Patrón: `<TIPO_HASH>`
- Ejemplo: `<DNI_a1b2c3>`, `<NOMBRE_x7y8z9>`
- El hash se genera a partir del valor original (SHA-256 truncado a 6 chars)

### 2. Tipos de Datos Predefinidos
| Tipo | Ejemplo | Regex de validación |
|------|---------|---------------------|
| DNI | 12345678Z | `\d{8}[A-Z]` |
| NIE | X1234567A | `[XYZ]\d{7}[A-Z]` |
| NOMBRE | Juan García | `[A-Za-záéíóúñÁÉÍÓÚÑ\s]{2,}` |
| TELEFONO | 612345678 | `\d{9}` |
| EMAIL | user@email.com | `[\w.-]+@[\w.-]+\.\w+` |
| DIRECCION | Calle Mayor 1 | `.+` |
| PERSONALIZADO | (definido por usuario) | `.*` |

### 3. API Principal

```java
// Ofuscar un prompt
String ofuscado = engine.ofuscar("Mi DNI es 12345678Z y me llamo Juan");
// Resultado: "Mi DNI es <DNI_a1b2c3> y me llamo <NOMBRE_x7y8z9>"

// Restaurar valores en respuesta del AI
String restaurado = engine.restaurar("El ciudadano <DNI_a1b2c3> está registrado");
// Resultado: "El ciudadano 12345678Z está registrado"

// Ofuscar solo tipos específicos
String ofuscado = engine.ofuscar("Mi DNI es 12345678Z", SensitiveDataType.DNI);
```

### 4. Almacenamiento (Storage)
- **Interfaz `StorageService`** para desacoplar la implementación
- **`InMemoryStorageService`** por defecto (ConcurrentHashMap)
- Soporte futuro: Redis, JPA, etc.

### 5. Configuración (application.yml)
```yaml
obfuscador:
  enabled: true
  hash-algorithm: SHA-256
  hash-length: 6
  storage-type: memory
  types:
    - DNI
    - NIE
    - NOMBRE
    - TELEFONO
    - EMAIL
  custom-types:
    CODIGO_POSTAL:
      pattern: "\d{5}"
    N_CUENTA:
      pattern: "ES\d{22}"
```

---

## Dependencias

### obfuscador-core
- Java 17+
- No dependencias externas (solo JDK)

### obfuscador-spring-boot-starter
- Spring Boot 3.x
- Spring AOP (para interceptores)
- obfuscador-core

---

## Flujo de Uso

```
1. Usuario configura la librería en su proyecto
2. El usuario llama a engine.ofuscar(prompt)
3. El motor detecta datos sensibles usando regex
4. Se genera hash del valor y se crea etiqueta <TIPO_HASH>
5. Se almacena mapeo hash → valor original en Storage
6. Se retorna prompt ofuscado
7. El prompt se envía al AI
8. AI responde con etiquetas
9. Usuario llama a engine.restaurar(respuesta)
10. Se reemplazan etiquetas por valores originales desde Storage
```

---

## Pasos de Implementación

1. **Crear estructura Maven** (pom.xml raíz + módulos)
2. **Implementar obfuscador-core**:
   - Modelos (ObfuscationTag, SensitiveData, ObfuscationConfig)
   - HashGenerator
   - DataTypeRegistry + SensitiveDataType enum
   - StorageService + InMemoryStorageService
   - ObfuscationEngine (lógica principal)
   - Excepciones personalizadas
3. **Implementar obfuscador-spring-boot-starter**:
   - ObfuscationProperties
   - ObfuscationAutoConfiguration
   - Anotación @Obfuscate
   - ObfuscationInterceptor (AOP)
4. **Tests unitarios** del core
5. **Ejemplo de uso** con proyecto de ejemplo

---

## Decisiones de Diseño

- **Hash SHA-256 truncado**: Balance entre unicidad y legibilidad
- **Storage flexible**: Interface permite cambiar implementación sin cambiar API
- **Tipos personalizables**: El usuario puede registrar sus propios patrones
- **Bidireccional**: Mantiene el mapeo para restaurar en respuestas
- **In-memory por defecto**: Simple para empezar, escalable con implementaciones futuras
