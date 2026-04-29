#!/bin/bash
# Démarrage du microservice SmartIntern AI

echo "SmartIntern AI — CV & Document Service"
echo "========================================"

# Vérifier le .env
if [ ! -f ".env" ]; then
    echo "ERREUR : Fichier .env introuvable"
    echo "Copiez .env.example en .env et renseignez ANTHROPIC_API_KEY"
    exit 1
fi

# Charger le port depuis .env
PORT=$(grep CV_SERVICE_PORT .env | cut -d'=' -f2)
PORT=${PORT:-8000}

echo "Démarrage sur le port $PORT..."
uvicorn main:app --host 0.0.0.0 --port $PORT --reload
