import numpy as np
import pandas as pd
from statsmodels.tsa.arima.model import ARIMA

def verifier_rythme_cardiaque(points) :
    df = pd.DataFrame(list(points), columns=["value"])

    data_process = []
    for i in range(1, len(df)) :
        data_process.append([df['value'].values[i] - df['value'].values[i-1]])
    df_preprocess = pd.DataFrame(data_process, columns=['value'])

    model = ARIMA(df_preprocess.value, order=(1,0,2))
    model_fit = model.fit()

    start = 1440

    prediction = model_fit.predict(dynamic=False, start=start)

    data_reconstruit = []
    data_reconstruit.append([df['value'].values[start]])
    for i in range(start, len(df)-1) :
        data_reconstruit.append([df['value'].values[i]+prediction[i]])
    df_reconstruit = pd.DataFrame(data_reconstruit, columns=['value'])

    est_important = np.zeros((len(df)-start))
    for i in range(0, len(df)-start) :
        if np.abs(points[i+start] - df_reconstruit['value'].values[i]) > 20 :
            est_important[i] = 1

    return est_important