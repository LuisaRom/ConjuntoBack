# Asistente IA y gestión inteligente

## Qué incluye

| Función | Quién la usa | Endpoint |
|---|---|---|
| Chat con acceso a la base de datos | Residente, celador, administrador | `POST /api/ia/chat` |
| Historial de conversaciones | Todos (solo las propias) | `GET /api/ia/conversaciones`, `GET /api/ia/conversaciones/{id}/mensajes`, `DELETE /api/ia/conversaciones/{id}` |
| Calificar respuestas (aprendizaje) | Todos | `POST /api/ia/mensajes/{id}/feedback` `{ "util": true }` |
| Base de conocimiento | Administrador | `GET/POST /api/ia/conocimiento`, `PUT /api/ia/conocimiento/{id}`, `PUT .../aprobar`, `PUT .../rechazar`, `DELETE` |
| Respuestas mal calificadas | Administrador | `GET /api/ia/feedback/negativos` |
| Resumen ejecutivo del conjunto | Administrador | `GET /api/ia/gestion/resumen` |
| Redacción de comunicados | Administrador | `POST /api/ia/comunicados/redactar` `{ "tema", "tono", "detalles" }` |
| Clasificar y priorizar una queja | Administrador, celador | `POST /api/ia/quejas/{id}/analizar` |
| Estado del asistente | Todos | `GET /api/ia/estado` |

Cada queja nueva se analiza automáticamente en segundo plano: categoría, prioridad (ALTA/MEDIA/BAJA), resumen y respuesta sugerida (`prioridadIA`, `categoriaIA`, `resumenIA`, `respuestaSugeridaIA`).

## Cómo consulta la base de datos

El modelo no ejecuta SQL. Usa herramientas definidas en `IA/HerramientasIA.java` y cada herramienta filtra por el rol del usuario autenticado:

- Residente: solo sus quejas, pagos, paquetes, mascotas y accesos de su apartamento. Las reservas de otros aparecen sin nombre.
- Celador: todo lo operativo (paquetes, accesos, reservas, quejas, mascotas, residentes), sin pagos.
- Administrador: todo, incluidos pagos, mora y resumen del conjunto.

## Cómo aprende (auto-entrenamiento)

No se reentrena el modelo; el asistente acumula conocimiento propio del conjunto en la tabla `conocimiento_ia` y lo recupera en cada pregunta:

1. Al arrancar se siembran las reglas reales de reservas, pagos, quejas, paquetería y accesos.
2. Cuando alguien enseña una norma o dato general en el chat, el asistente lo guarda. Si lo enseña el administrador queda aprobado; si lo enseña otro rol queda pendiente.
3. Cuando un usuario califica una respuesta como útil y esa respuesta no usó datos personales, la pareja pregunta/respuesta se propone como conocimiento pendiente.
4. El administrador aprueba o rechaza en la app (Gestión IA > Conocimiento) y responde las preguntas mal calificadas (Gestión IA > Mejorar).
5. Cada conversación conserva sus últimos 20 mensajes como memoria de contexto.

Nada aprendido de residentes llega a otros usuarios sin aprobación, para evitar filtrar datos privados o información incorrecta.

## Configuración (variables de entorno)

| Variable | Ejemplo | Descripción |
|---|---|---|
| `IA_PROVEEDOR` | `anthropic` u `openai` | `openai` sirve para cualquier API compatible (OpenAI, Gemini, Groq, Ollama) |
| `IA_API_KEY` | | Clave del proveedor. Sin clave el asistente funciona en modo básico con la base de conocimiento |
| `IA_MODELO` | `claude-haiku-5-5`, `gpt-4o-mini`, `gemini-2.5-flash` | Modelo a usar |
| `IA_BASE_URL` | `https://generativelanguage.googleapis.com/v1beta/openai` | Solo para proveedores compatibles con OpenAI distintos de OpenAI |
| `IA_NOMBRE_CONJUNTO` | `Conjunto Residencial Los Pinos` | Nombre usado por el asistente |
| `IA_NOMBRE_ASISTENTE` | `Asistente del Conjunto` | Nombre del asistente |
| `IA_LIMITE_MENSAJES_HORA` | `40` | Límite por usuario para controlar costos |

En Render se agregan en Environment del servicio. Las tablas `conocimiento_ia`, `conversacion_ia`, `mensaje_ia` y las columnas nuevas de `queja` las crea Hibernate (`ddl-auto=update`).
