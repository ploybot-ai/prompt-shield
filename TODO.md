# TODO / Mejoras pendientes

## Alta prioridad
- [x] Publicar en Maven Central (com.ploybot)
- [x] Documentación completa (README con ejemplos de uso)

## Media prioridad
- [ ] Integrar librería de detección de entidades (Named Entity Recognition)
  - **Objetivo**: Detectar automáticamente entidades sensibles (DNI, email, teléfonos, etc.) sin depender de patrones regex
  - **Opciones posibles**:
    - **A) Apache OpenNLP**: Open source, soporta NER en español
    - **B) Stanford CoreNLP**: Más preciso, pero pesado
    - **C) Lingua**: Ligero, multi-idioma, detección de idioma
    - **D) Custom ML model**: Entrenar modelo específico para datos españoles
  - **Recomendación**: Apache OpenNLP o Lingua (balance entre precisión y recursos)
- [ ] Soporte para ofuscar números y booleanos en JSON
  - **Problema actual**: Solo se ofuscan strings. Numbers y booleans quedan sin modificar.
  - **Opciones posibles**:
    - **A) Campo companion**: Añadir campo `_field_hash` con el hash del valor original
    - **B) Solo strings**: No ofuscar números/booleanos (estado actual)
    - **C) Fake values únicos**: Usar `hash.hashCode() % 1000000` en vez de `0`
    - **D) Wrapper object**: Envolver en `{"_obfuscated": true, "data": {...}, "hashes": {...}}`
  - **Recomendación**: Opción A (balance entre simplicidad y funcionalidad)
- [x] Tests de integración con Spring AI
- [x] Soporte para Redis como StorageService
- [x] Soporte para JPA/H2 como StorageService

## Baja prioridad
- [ ] Métricas de obfuscación (cuántos datos se obfuscan por request)
- [ ] Rate limiting por tipo de dato
- [ ] Soporte para obfuscar en respuestas del AI (no solo prompts)
- [ ] Benchmark de rendimiento
