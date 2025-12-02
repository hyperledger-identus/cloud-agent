FROM python:3.12@sha256:01f36278bcca9fb4a5bdda7edc85be44218190a9371f642d8f852f36ad9fa23d
WORKDIR /workspace
COPY requirements.txt /workspace/requirements.txt
RUN pip install -r requirements.txt
COPY . /workspace
CMD ["python", "main.py"]
