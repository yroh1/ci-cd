FROM python:3.8-slim as serve
WORKDIR /usr/src/app
# Copy all packages instead of rerunning pip install
COPY ./flask-api/ ./
RUN  pip install -r requirements.txt 
CMD ["python", "run_app.py"]

