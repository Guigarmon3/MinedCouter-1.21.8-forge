# Block Counter (Forge 1.21.8)

HUD compacto para singleplayer: total de bloques minados del mundo (grande) y de la sesión (pequeño).

## Requisitos
- JDK 21
- Forge 1.21.8-58.0.3 (se descarga solo con Gradle)

## Ejecutar
```
./gradlew runClient      # Linux / macOS
gradlew.bat runClient    # Windows
```
Para generar el .jar: `./gradlew build` (sale en `build/libs/`).

## Cómo funciona
- El total sale de las estadísticas vanilla (`Stats.BLOCK_MINED`) leídas del servidor integrado, así que es el número real del mundo.
- La sesión es la diferencia respecto al valor al abrir el mundo (sigue contando aunque mueras o cambies de dimensión).
- En servidores remotos no dibuja nada.
- Igual que vanilla, el modo creativo no cuenta bloques.

## Ajustes rápidos
En `BlockCounterHud.java`, arriba del todo:
- `MARGIN_X` / `MARGIN_Y`: posición del panel.
- `AUTO_HIDE_SECONDS`: 0 = siempre visible; p. ej. 5 = se oculta 5 s después del último bloque.
- `TOTAL_SCALE`: tamaño del número grande.
