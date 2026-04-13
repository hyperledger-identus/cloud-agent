FROM python:3.14@sha256:8428c32065df71d5a7763099ccb8ee215314129b50d395f95ad589151887d52f
WORKDIR /workspace
COPY requirements.txt /workspace/requirements.txt
RUN pip install -r requirements.txt
COPY . /workspace
CMD ["python", "main.py"]
