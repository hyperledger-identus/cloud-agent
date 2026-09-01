FROM python:3.14@sha256:8edbf9e42c7fb168b9c523718ed907117e6d2e60f5889c0c499bbda3a787da53
WORKDIR /workspace
COPY requirements.txt /workspace/requirements.txt
RUN pip install -r requirements.txt
COPY . /workspace
CMD ["python", "main.py"]
