import os
import pandas as pd
import numpy as np
import joblib
import json
from tensorflow.keras.models import load_model

# Tắt hết log/warning của TensorFlow và Python
os.environ['TF_CPP_MIN_LOG_LEVEL'] = '3'  # 3 = tắt INFO/WARNING/ERROR
os.environ['TF_ENABLE_ONEDNN_OPTS'] = '0'  # Tắt thêm nếu dùng Intel CPU
import warnings
warnings.simplefilter("ignore")
warnings.filterwarnings("ignore")

CURRENT_DIR = os.path.dirname(os.path.abspath(__file__))
DATA_PATH = os.path.join(CURRENT_DIR, "..", "data", "PJME_hourly.csv")
MODEL_DIR = os.path.join(CURRENT_DIR, "models")

try:
    scaler = joblib.load(os.path.join(MODEL_DIR, "scaler.pkl"))
    lr = joblib.load(os.path.join(MODEL_DIR, "linear.pkl"))
    lstm = load_model(os.path.join(MODEL_DIR, "lstm.h5"), compile=False)  # compile=False để tránh log thừa
except Exception as e:
    print(json.dumps({"error": f"Không tải được mô hình: {str(e)}"}))
    exit(1)

df = pd.read_csv(DATA_PATH, parse_dates=['Datetime'], index_col='Datetime')
df.sort_index(inplace=True)

last_24 = df['PJME_MW'].tail(24).values.reshape(-1, 1)
last_24_scaled = scaler.transform(last_24)

# Linear
lr_pred = lr.predict(last_24_scaled.reshape(1, -1))
lr_value = scaler.inverse_transform(lr_pred.reshape(-1, 1))[0][0]

# LSTM
lstm_pred = lstm.predict(last_24_scaled.reshape(1, 24, 1), verbose=0)
lstm_value = scaler.inverse_transform(lstm_pred)[0][0]

result = {
    "linear_regression": round(float(lr_value), 2),
    "lstm": round(float(lstm_value), 2),
    "last_24_hours": [round(float(x), 2) for x in last_24.flatten()]
}

# In ra JSON sạch, không có gì thừa
print(json.dumps(result, ensure_ascii=False))