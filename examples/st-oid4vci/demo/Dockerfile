FROM python:3.14@sha256:edf6433343f65f94707985869aeaafe8beadaeaee11c4bc02068fca52dce28dd
WORKDIR /workspace
COPY requirements.txt /workspace/requirements.txt
RUN pip install -r requirements.txt
COPY . /workspace
CMD ["python", "main.py"]
