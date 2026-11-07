# Jarvis para Android

Asistente de voz que se activa al decir "Jarvis" (o el nombre que configures).

## Cómo usarlo
1. Abre la carpeta en Android Studio (Hedgehog o más nuevo). Sincroniza Gradle.
2. Ejecuta en tu teléfono.
3. En la app: pon el nombre que quieras, pega tu clave de API de Google Gemini (gratis en aistudio.google.com), pulsa Guardar.
4. Pulsa "Permitir abrir apps en segundo plano" y activa el permiso.
5. Pulsa Activar y di: "Jarvis, ponme una alarma a las 7".

## Qué hace
- Escucha en segundo plano (servicio en primer plano con notificación).
- Al oír el nombre, manda lo que dijiste a Gemini y responde por voz.
- Acciones: abrir apps, alarmas, temporizadores, búsqueda web, mapas.

## Limitaciones conocidas
- La escucha usa el reconocedor de Android: puede sonar un pitido al reiniciarse en algunos móviles y gasta batería. Desactiva la optimización de batería para la app.
- Para detección de palabra clave más eficiente se puede cambiar por Porcupine o Vosk.
- La clave de API se guarda en el teléfono; para publicar la app, mejor un servidor intermedio.
