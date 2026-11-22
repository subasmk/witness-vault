# Third-party notices

Witness Vault app code: Copyright 2026 subasmk, Apache-2.0.

The offline `facenet.tflite` model is redistributed from Shubham Panchal's
FaceRecognition_With_FaceNet_Android project (Apache-2.0), snapshot
`289bc10420aad15fed99094eee364eb24f908ecc`.

- Project: https://github.com/shubham0204/FaceRecognition_With_FaceNet_Android
- Model: https://github.com/shubham0204/FaceRecognition_With_FaceNet_Android/tree/master/app/src/main/assets
- Prewhitening reference: https://github.com/shubham0204/FaceRecognition_With_FaceNet_Android/blob/master/app/src/main/java/com/ml/quaterion/facenetdetection/model/FaceNetModel.kt
- License text: `app/src/main/assets/FACENET_LICENSE.txt`

FaceNet's predictions can be wrong. Model availability is not a claim of
certified identity verification, fairness, or presentation-attack resistance.

The app also uses AndroidX (Apache-2.0), TensorFlow Lite (Apache-2.0), and Google
ML Kit face detection, governed by the respective component licenses and
Google SDK terms. ML Kit detection models and FaceNet are bundled in the APK.
No cloud face service or paid API is used.

Transient developer smoke-test images came from DeepFace's public test dataset
and are not included in the application or repository:
https://github.com/serengil/deepface/tree/master/tests/unit/dataset
