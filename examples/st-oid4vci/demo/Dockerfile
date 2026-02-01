FROM python:3.14@sha256:17bc9f1d032a760546802cc4e406401eb5fe99dbcb4602c91628e73672fa749c
WORKDIR /workspace
COPY requirements.txt /workspace/requirements.txt
RUN pip install -r requirements.txt
COPY . /workspace
CMD ["python", "main.py"]
