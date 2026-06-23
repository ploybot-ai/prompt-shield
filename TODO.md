# TODO / Mejoras pendientes

## Alta prioridad
- [x] Publicar en Maven Central (com.ploybot)
- [x] Documentación completa (README con ejemplos de uso)
- [x] Cambiar el formato por <<REDACTED:EMAIL#81b562>> o ⟦REDACTED:EMAIL:1⟧ o __REDACTED_EMAIL_1__ o @@ , etc ... , revisar distintas alternativas probalas y ver cual funciona mejor
- [ ] **SEGURIDAD**: Aislar placeholders por conversación
  - **Vulnerabilidad**: Si un usuario malintencionado escribe un hash conocido en su prompt (ej: `~REDACTED:EMAIL#abc123~`), el engine podría restaurar el email de **otra persona** si el hash coincide en el storage compartido.
  - **Solución**: Clave el storage por `conversationId` o `sessionId`. Cada conversación tiene su propio namespace de hashes.
  - **Opciones**:
    - **A) Prefijo por conversación**: `conversationId:hash` como clave en StorageService
    - **B) Scope en ObfuscationConfig**: Añadir `conversationId` al config y filtrar restores por scope
    - **C) Token efímero**: Generar un token por request que namespacee los hashes
  - **Recomendación**: Opción A (mínimo cambio, máxima seguridad)
  - **Riesgo**: Sin esto, un atacante puede inyectar hashes para extraer datos ajenos

## Media prioridad
- [x] Actualizar a última versión de Spring Boot y Spring AI 2.0.0
  - **Current**: Spring Boot 3.3.0, Spring AI 1.0.0
  - **Target**: Spring Boot 3.4.x, Spring AI 2.0.0
  - **Acciones**:
    - Verificar compatibilidad de módulos
    - Actualizar APIs de Spring AI (cambios en ChatClient, Advisors)
    - Ejecutar tests completos
  - **Riesgo**: Puede haber breaking changes en Spring AI 2.0.0
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
- [ ] Publicar imagen Docker del API Proxy en Docker Hub
  - **Objetivo**: Facilitar despliegue del proxy OpenAI-compatible
  - **Acciones**:
    - Configurar build de Docker Hub en GitHub Actions
    - Publicar imagen multi-arch (amd64/arm64)
    - Documentar uso con Docker Compose
- [ ] Métricas de obfuscación (cuántos datos se obfuscan por request)
- [ ] Rate limiting por tipo de dato
- [ ] Soporte para obfuscar en respuestas del AI (no solo prompts)
- [ ] Benchmark de rendimiento
