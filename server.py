from flask import Flask, request, jsonify
import torch
from transformers import Wav2Vec2ForSequenceClassification, Wav2Vec2FeatureExtractor
import librosa
import numpy as np
import torch.nn.functional as F

app = Flask(__name__)

MODEL_DIR = r"C:\projects\tts_model\wav2vec2_clf_model2"

device = "cuda" if torch.cuda.is_available() else "cpu"
model = Wav2Vec2ForSequenceClassification.from_pretrained(MODEL_DIR).to(device)
extractor = Wav2Vec2FeatureExtractor.from_pretrained(MODEL_DIR)

label_map = {0: "orig", 1: "tts", 2: "tts_gsm"}

TARGET_RMS = 10 ** (-20 / 20)   # -20 dBFS


@app.route("/predict", methods=["POST"])
def predict():
    if "audio" not in request.files:
        return jsonify({"error": "No audio file"}), 400

    file = request.files["audio"]
    wav_path = "input.wav"
    file.save(wav_path)

    try:
        # 1) load
        wav, sr = librosa.load(wav_path, sr=16000, mono=True)

        # 2) conditional trim (완화)
        if np.max(np.abs(wav)) > 0.01:
            wav, _ = librosa.effects.trim(wav, top_db=35)

        # 3) 최소 길이 체크 (에러 대신 soft 처리)
        if len(wav) < int(16000 * 0.4):
            return jsonify({
                "result": "unknown",
                "scores": {"orig": 0.0, "tts": 0.0, "tts_gsm": 0.0}
            })

        # 4) RMS normalize ONLY
        rms = np.sqrt(np.mean(wav ** 2))
        if rms > 1e-6:
            wav = wav * (TARGET_RMS / rms)

        # 5) extractor
        inputs = extractor(
            wav,
            sampling_rate=16000,
            return_tensors="pt",
            padding=True,
            truncation=False
        )
        inputs = {k: v.to(device) for k, v in inputs.items()}

        # 6) inference
        with torch.no_grad():
            logits = model(**inputs).logits
            probs = F.softmax(logits, dim=-1).cpu().numpy()[0]

        pred = int(np.argmax(probs))
        result_label = label_map[pred]

        score_dict = {
            label_map[i]: float(probs[i])
            for i in range(len(probs))
        }

        return jsonify({
            "result": result_label,
            "scores": score_dict
        })

    except Exception as e:
        print("ERROR:", e)
        return jsonify({"error": str(e)}), 500


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000, debug=False)
