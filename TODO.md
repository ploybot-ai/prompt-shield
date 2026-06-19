# TODO / Mejoras pendientes

## Alta prioridad
- [ ] Publicar en Maven Central (com.ploybot)
- [ ] Documentación completa (README con ejemplos de uso)

## Media prioridad
- [ ] Soporte para ofuscar números y booleanos en JSON
  - **Problema actual**: Solo se ofuscan strings. Numbers y booleans quedan sin modificar.
  - **Opciones posibles**:
    - **A) Campo companion**: Añadir campo `_field_hash` con el hash del valor original
    - **B) Solo strings**: No ofuscar números/booleanos (estado actual)
    - **C) Fake values únicos**: Usar `hash.hashCode() % 1000000` en vez de `0`
    - **D) Wrapper object**: Envolver en `{"_obfuscated": true, "data": {...}, "hashes": {...}}`
  - **Recomendación**: Opción A (balance entre simplicidad y funcionalidad)
- [x] Tests de integración con Spring AI
- [ ] Soporte para Redis como StorageService
- [ ] Soporte para JPA/H2 como StorageService

## Baja prioridad
- [ ] Métricas de obuscação (cuántos datos se ofuscan por request)
- [ ] Rate limiting por tipo de dato
- [ ] Soporte para ofuscar en respuestas del AI (no solo prompts)
- [ ] Benchmark de rendimiento
