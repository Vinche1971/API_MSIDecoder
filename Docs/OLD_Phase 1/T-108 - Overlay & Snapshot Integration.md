# T-108 : Overlay & Snapshot Integration

## 🎯 Objectif
Afficher les infos MSI clés et enrichir le snapshot JSON.

## 🛠 Techniques
- Overlay : `MSI: ROI=2 angle=-3.2° w=3.4px runs=142 score=0.81`.
- Snapshot `msiDbg` :
  ```json
  {
    "roiCount": 2,
    "roiChosen": 0,
    "angleDeg": -3.2,
    "wPx": 3.4,
    "runsPxCount": 142,
    "runsQuantSample": [1,2,1,1,2,...],
    "quantTol": 0.35,
    "snr": 18.7
  }
