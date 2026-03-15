import pandas as pd
import numpy as np
import random
import json
import java
from datetime import timedelta
from os.path import join, dirname
from java import jarray, jclass
from org.tensorflow.lite import Interpreter
from java.io import File

# Variables globales
interpreter = None
class_names = None
WINDOW_SIZE = 14
MAX_CYCLE_LEN = 50.0

def init_model(files_dir):
    """Initialise le modèle TFLitey"""
    global interpreter, class_names
    if interpreter is None:
        model_path = join(files_dir, "LSTM_model.tflite")

        interpreter = Interpreter(File(model_path))

        class_names = ['fertile', 'other', 'period']

def preprocess_window(df_window, user_avg_len, user_avg_per):
    """Prépare UNE fenêtre pour les 5 branches du modèle"""
    # Normalisation
    norm_bbt = (df_window['BasaleTemperature'].values - 35.5) / 2.0
    norm_hr = (df_window['RestingHeartRate'].values - 40.0) / 70.0
    norm_stress = df_window['RealStress'].values / 100
    norm_sleep = df_window['SleepScore'].values / 100
    norm_phq9 = df_window['DepressionScore'].values / 27
    norm_gad7 = df_window['AnxietyScore'].values / 21
    act = df_window['ActivityScore'].values

    raw_day_idxs = df_window['DayInCycle'].values
    day_idxs = raw_day_idxs / MAX_CYCLE_LEN
    day_sin = np.sin(2 * np.pi * raw_day_idxs / user_avg_len)
    day_cos = np.cos(2 * np.pi * raw_day_idxs / user_avg_len)
    period = df_window['IsOnPeriod'].values

    X_core = np.expand_dims(np.stack([norm_bbt, norm_hr, day_idxs, day_sin, day_cos, period], axis=1), axis=0).astype(np.float32)

    X_mental = np.expand_dims(np.stack([norm_stress, norm_phq9, norm_gad7], axis=1), axis=0).astype(np.float32)

    X_sleep = np.expand_dims(np.expand_dims(norm_sleep, axis=1), axis=0).astype(np.float32)

    X_act = np.expand_dims(np.expand_dims(act, axis=1), axis=0).astype(np.float32)

    norm_cycle = user_avg_len / MAX_CYCLE_LEN
    norm_period = user_avg_per / 10.0
    is_reg = df_window['is_regular'].values[0] if 'is_regular' in df_window.columns else 1.0
    X_static = np.array([[norm_cycle, norm_period, is_reg]], dtype=np.float32)

    return X_core, X_mental, X_sleep, X_act, X_static

def aggregate_daily_data(df):
    df['isoDate'] = pd.to_datetime(df['isoDate']).dt.date
    for col in ['BasaleTemperature', 'RestingHeartRate', 'SleepScore', 'ActivityScore', 'RealStress', 'DepressionScore', 'AnxietyScore']:
        df[col] = df[col].where(df[col] > 0, np.nan)

    agg_rules = {
        'RestingHeartRate': 'min', 'IsOnPeriod': 'first', 'BasaleTemperature': 'min',
        'CycleID': 'first', 'DayInCycle': 'first', 'AvgCycleLength': 'first',
        'AvgPeriodLength': 'first', 'SleepScore': 'first', 'ActivityScore': 'first',
        'RealStress': 'first', 'DepressionScore': 'first', 'AnxietyScore': 'first'
    }

    df_daily = df.groupby('isoDate', as_index=False).agg(agg_rules)
    df_daily = df_daily.sort_values('isoDate').reset_index(drop=True)
    for col in ['BasaleTemperature', 'RestingHeartRate', 'SleepScore', 'ActivityScore', 'RealStress', 'DepressionScore', 'AnxietyScore']:
        df_daily[col] = df_daily[col].fillna(df_daily[col].mean() if not df_daily[col].isna().all() else 0)
    return df_daily

def inject_synthetic_history(df):
    first_row = df.iloc[0]
    target_day = int(first_row['DayInCycle'])
    first_cycle_id = first_row['CycleID']
    avg_len = int(first_row['AvgCycleLength']) if pd.notna(first_row['AvgCycleLength']) else 28
    avg_per = int(first_row['AvgPeriodLength']) if pd.notna(first_row['AvgPeriodLength']) else 6
    start_date = first_row['isoDate']

    base_temp = df['BasaleTemperature'].mean()
    base_hr = df['RestingHeartRate'].mean()
    mean_sleep = df['SleepScore'].mean()
    mean_stress = df['RealStress'].mean()
    mean_act = df['ActivityScore'].mean()
    first_dep = first_row['DepressionScore']
    first_anx = first_row['AnxietyScore']

    cycle_lengths = [avg_len, avg_len]
    cycle_ids = [first_cycle_id - 2, first_cycle_id - 1]
    if target_day > 1:
        cycle_lengths.append(target_day - 1)
        cycle_ids.append(first_cycle_id)

    total_days = sum(cycle_lengths)
    synthetic_data = []
    current_date = start_date - timedelta(days=total_days)

    for c_idx, length in enumerate(cycle_lengths):
        c_id = cycle_ids[c_idx]
        ovulation_day = avg_len - 14
        for day in range(1, length + 1):
            act = min(1.0, max(0.0, mean_act + random.uniform(-0.2, 0.2)))
            sleep_score = max(0.0, min(100.0, mean_sleep + random.uniform(-20, 20)))
            stress_score = max(0.0, min(100.0, mean_stress + random.uniform(-20, 20)))
            temp = base_temp + random.uniform(-0.1, 0.1)
            hr = base_hr + random.uniform(-1, 1)

            row = {
                'isoDate': current_date, 'BasaleTemperature': round(temp, 2),
                'RestingHeartRate': round(hr, 1), 'SleepScore': round(sleep_score, 1),
                'ActivityScore': round(act, 2), 'RealStress': round(stress_score, 1),
                'DepressionScore': float(first_dep), 'AnxietyScore': float(first_anx),
                'IsOnPeriod': 1.0 if day <= avg_per else 0.0, 'CycleID': c_id,
                'DayInCycle': float(day), 'AvgCycleLength': float(avg_len), 'AvgPeriodLength': float(avg_per)
            }
            if 'is_regular' in df.columns: row['is_regular'] = df['is_regular'].iloc[0]
            synthetic_data.append(row)
            current_date += timedelta(days=1)

    return pd.concat([pd.DataFrame(synthetic_data), df], ignore_index=True)

def predict_from_kotlin(files_dir, user_data_json):
    init_model(files_dir)
    df_raw = pd.read_json(user_data_json)
    df_full = aggregate_daily_data(df_raw)

    target_cycle_num = df_full['CycleID'].max()
    first_target_idx = df_full[df_full['CycleID'] == target_cycle_num].index[0]

    if first_target_idx < WINDOW_SIZE - 1:
        df_full = inject_synthetic_history(df_full)
        target_cycle_num = df_full['CycleID'].max()

    history_data = df_full[df_full['CycleID'] < target_cycle_num]
    if not history_data.empty:
        calc_avg_len = history_data.groupby('CycleID')['DayInCycle'].max().mean()
        calc_avg_per = history_data.groupby('CycleID')['IsOnPeriod'].sum().mean()
    else:
        calc_avg_len = df_full['AvgCycleLength'].iloc[-1]
        calc_avg_per = df_full['AvgPeriodLength'].iloc[-1]

    current_day = df_full[df_full['CycleID'] == target_cycle_num]['DayInCycle'].values[-1]
    idx_in_full = df_full[(df_full['CycleID'] == target_cycle_num) & (df_full['DayInCycle'] == current_day)].index[-1]
    df_window = df_full.iloc[idx_in_full - WINDOW_SIZE + 1: idx_in_full + 1]

    X_core, X_mental, X_sleep, X_act, X_static = preprocess_window(df_window, calc_avg_len, calc_avg_per)

    JFloatArray2D = jarray(jarray("F"))
    JFloatArray3D = jarray(jarray(jarray("F")))

    X_core_j = JFloatArray3D(X_core.tolist())
    X_mental_j = JFloatArray3D(X_mental.tolist())
    X_sleep_j = JFloatArray3D(X_sleep.tolist())
    X_act_j = JFloatArray3D(X_act.tolist())
    X_static_j = JFloatArray2D(X_static.tolist())

    ObjectClass = jclass("java.lang.Object")
    inputs = jarray(ObjectClass)([X_core_j, X_mental_j, X_sleep_j, X_act_j, X_static_j])

    out_length_j = JFloatArray2D([[0.0]])
    out_ovu_j = JFloatArray2D([[0.0]])
    out_status_j = JFloatArray2D([[0.0, 0.0, 0.0]])

    HashMap = jclass("java.util.HashMap")
    Integer = jclass("java.lang.Integer")
    outputs_j = HashMap()

    outputs_j.put(Integer(0), out_length_j)
    outputs_j.put(Integer(1), out_ovu_j)
    outputs_j.put(Integer(2), out_status_j)

    input_count = interpreter.getInputTensorCount()
    inputs_list = [None] * input_count

    for i in range(input_count):
        tensor = interpreter.getInputTensor(i)
        shape = list(tensor.shape())
        name = str(tensor.name()).lower()

        if len(shape) == 2:
            inputs_list[i] = X_static_j
        elif len(shape) == 3:
            if shape[2] == 6:
                inputs_list[i] = X_core_j
            elif shape[2] == 3:
                inputs_list[i] = X_mental_j
            elif shape[2] == 1:
                if "sleep" in name:
                    inputs_list[i] = X_sleep_j
                else:
                    inputs_list[i] = X_act_j

    ObjectClass = jclass("java.lang.Object")
    inputs = jarray(ObjectClass)(inputs_list)


    out_1_j = JFloatArray2D([[0.0]])
    out_2_j = JFloatArray2D([[0.0]])
    out_status_j = JFloatArray2D([[0.0, 0.0, 0.0]])

    HashMap = jclass("java.util.HashMap")
    Integer = jclass("java.lang.Integer")
    outputs_j = HashMap()

    single_value_idx = []
    output_count = interpreter.getOutputTensorCount()
    for i in range(output_count):
        tensor = interpreter.getOutputTensor(i)
        shape = list(tensor.shape())

        if len(shape) == 2 and shape[1] == 3:
            outputs_j.put(Integer(i), out_status_j)
        elif len(shape) == 2 and shape[1] == 1:
            single_value_idx.append(i)

    if len(single_value_idx) >= 2:
        outputs_j.put(Integer(single_value_idx[0]), out_1_j)
        outputs_j.put(Integer(single_value_idx[1]), out_2_j)

    interpreter.runForMultipleInputsOutputs(inputs, outputs_j)

    val1 = out_1_j[0][0] * MAX_CYCLE_LEN
    val2 = out_2_j[0][0] * MAX_CYCLE_LEN

    if val1 > val2:
        predicted_total_length = val1
        predicted_ovulation_day = val2
    else:
        predicted_total_length = val2
        predicted_ovulation_day = val1

    status_probs = [out_status_j[0][0], out_status_j[0][1], out_status_j[0][2]]
    statut_label = class_names[np.argmax(status_probs)]

    alpha = min(current_day / calc_avg_len, 0.9)
    blended_length = (1 - alpha) * calc_avg_len + alpha * predicted_total_length
    estimated_ovu = round((1 - alpha) * (calc_avg_len - 14.0) + alpha * predicted_ovulation_day)

    final_status = statut_label
    if final_status == "other": final_status = "follicular" if current_day < estimated_ovu else "luteal"
    if current_day <= 3: final_status = "period"
    elif final_status == "period" and current_day > calc_avg_per: final_status = "follicular" if current_day < estimated_ovu else "luteal"

    fertile_start, fertile_end = estimated_ovu - 5, estimated_ovu + 2
    if final_status == "fertile" and not (fertile_start <= current_day <= fertile_end):
        final_status = "follicular" if current_day < estimated_ovu else "luteal"
    if final_status not in ["period", "fertile"]:
        final_status = "follicular" if current_day < estimated_ovu else "luteal"

    fertility_prob = "Unlikely"
    if (estimated_ovu - 2) <= current_day <= (estimated_ovu + 1): fertility_prob = "High"
    elif fertile_start <= current_day <= fertile_end: fertility_prob = "Medium"
    elif current_day in [fertile_start - 1, fertile_end + 1]: fertility_prob = "Low"

    return json.dumps({
        "predicted_cycle_length": int(round(blended_length)),
        "predicted_ovulation": int(estimated_ovu),
        "status": final_status.capitalize(),
        "fertility_prob": fertility_prob
    })
