# PlantVisReborn 🌿

Android-приложение для определения растений с помощью AI.

## Интегрированная нейросеть

**TensorFlow Lite + MobileNetV2** (Apache 2.0)
- GitHub: https://github.com/tensorflow/tensorflow
- Специально оптимизирован для мобильных устройств
- Поддержка GPU-ускорения через TFLite GPU Delegate

---

## ⚠️ Шаг 1: Скачать модель

Проект требует файл модели `plant_classifier.tflite` в папке:
```
app/src/main/assets/plant_classifier.tflite
```

### Вариант A — MobileNetV2 (общий, ImageNet 1000 классов)
Подходит для быстрого старта, распознаёт некоторые растения:
```bash
# Скачать с TFHub
wget https://storage.googleapis.com/download.tensorflow.org/models/tflite/mobilenet_v2_1.0_224.tflite \
     -O app/src/main/assets/plant_classifier.tflite
```

### Вариант B — EfficientNet на Oxford 102 Flowers (рекомендуется)
Специально обучен на цветах, 102 вида:
1. Перейти: https://www.kaggle.com/models/google/aiy-vision-classifier-plants-v1
2. Скачать файл `.tflite`
3. Сохранить как `app/src/main/assets/plant_classifier.tflite`

### Вариант C — PlantNet TFLite
Крупнейшая база растений, 10 000+ видов:
1. Перейти: https://github.com/plantnet/plant-id-api
2. Следовать инструкциям по экспорту модели

---

## Архитектура

```
MainActivity.kt          ← UI: камера/галерея, отображение результатов
PlantClassifier.kt       ← Обёртка TFLite: загрузка модели, инференс
assets/
  plant_classifier.tflite  ← Модель (нужно скачать)
res/layout/
  activity_main.xml      ← Разметка экрана
```

## Зависимости

```kotlin
// build.gradle.kts
implementation("org.tensorflow:tensorflow-lite-task-vision:0.4.4")
implementation("org.tensorflow:tensorflow-lite-gpu-delegate-plugin:0.4.4")
implementation("org.tensorflow:tensorflow-lite-gpu:2.14.0")
```

## Как заменить модель

В `PlantClassifier.kt` просто смените константу:
```kotlin
private const val MODEL_FILE = "your_model.tflite"
```

Если у модели другой размер входа (не 224×224):
```kotlin
private const val INPUT_SIZE = 320  // например
```

---

## Лицензии

- TensorFlow Lite: [Apache 2.0](https://github.com/tensorflow/tensorflow/blob/master/LICENSE)
- Приложение PlantVisReborn: MIT
