import os
import pandas as pd
import numpy as np
import joblib
from sklearn.preprocessing import MinMaxScaler
from sklearn.linear_model import LinearRegression
from tensorflow.keras.models import Sequential
from tensorflow.keras.layers import LSTM, Dense
from sklearn.metrics import mean_absolute_error, mean_squared_error

print("Bắt đầu huấn luyện mô hình...")

CURRENT_DIR = os.path.dirname(os.path.abspath(__file__))
DATA_PATH = os.path.join(CURRENT_DIR, "..", "data", "PJME_hourly.csv")
MODEL_DIR = os.path.join(CURRENT_DIR, "models")
os.makedirs(MODEL_DIR, exist_ok=True)

# Tiền xử lý dữ liệu
df = pd.read_csv(DATA_PATH, parse_dates=['Datetime'], index_col='Datetime')
df.sort_index(inplace=True)
df = df.dropna()  # Xử lý thiếu
mean = df['PJME_MW'].mean()
std = df['PJME_MW'].std()
df = df[(df['PJME_MW'] > mean - 3*std) & (df['PJME_MW'] < mean + 3*std)]  # Loại ngoại lệ
df['hour'] = df.index.hour  # Tạo đặc trưng thời gian
df['day'] = df.index.day
df['month'] = df.index.month
data = df.values  # Bao gồm features mới

scaler = MinMaxScaler()
data_scaled = scaler.fit_transform(data)

def create_sequences(data, seq_length=24):
    X, y = [], []
    for i in range(len(data) - seq_length):
        X.append(data[i:i + seq_length])
        y.append(data[i + seq_length][0])  # Dự báo PJME_MW
    return np.array(X), np.array(y)

X, y = create_sequences(data_scaled, 24)
train_size = int(len(X) * 0.8)
X_train, X_test = X[:train_size], X[train_size:]
y_train, y_test = y[:train_size], y[train_size:]

# Linear Regression
lr = LinearRegression()
lr.fit(X_train.reshape(X_train.shape[0], -1), y_train.ravel())
lr_pred = lr.predict(X_test.reshape(X_test.shape[0], -1))

# LSTM
model = Sequential([
    LSTM(64, return_sequences=True, input_shape=(24, data_scaled.shape[1])),
    LSTM(64),
    Dense(1)
])
model.compile(optimizer='adam', loss='mse')
model.fit(X_train, y_train, epochs=10, batch_size=32, verbose=1)
lstm_pred = model.predict(X_test).flatten()

# Tính metrics
def calculate_metrics(y_true, y_pred):
    mae = mean_absolute_error(y_true, y_pred)
    rmse = mean_squared_error(y_true, y_pred, squared=False)
    mape = np.mean(np.abs((y_true - y_pred) / y_true)) * 100
    return mae, rmse, mape

lr_mae, lr_rmse, lr_mape = calculate_metrics(y_test, lr_pred)
lstm_mae, lstm_rmse, lstm_mape = calculate_metrics(y_test, lstm_pred)

# Lưu mô hình
joblib.dump(scaler, os.path.join(MODEL_DIR, "scaler.pkl"))
joblib.dump(lr, os.path.join(MODEL_DIR, "linear.pkl"))
model.save(os.path.join(MODEL_DIR, "lstm.h5"))

print("HUẤN LUYỆN HOÀN TẤT!")
print(f"Metrics Linear: MAE={lr_mae:.2f}, RMSE={lr_rmse:.2f}, MAPE={lr_mape:.2f}%")
print(f"Metrics LSTM: MAE={lstm_mae:.2f}, RMSE={lstm_rmse:.2f}, MAPE={lstm_mape:.2f}%")
print(f"Mô hình đã lưu tại: {MODEL_DIR}")