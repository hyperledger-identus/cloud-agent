FROM python:3.14@sha256:0ba001803c72c128063cfa88863755f905cefabe73c026c66a5a86d8f1d63e98
WORKDIR /workspace
COPY requirements.txt /workspace/requirements.txt
RUN pip install -r requirements.txt
COPY . /workspace
CMD ["python", "main.py"]
