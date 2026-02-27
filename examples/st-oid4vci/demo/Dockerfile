FROM python:3.14@sha256:f05033a4c0ff84db95fd7e6cb361b940a260703d1cd63c63b3472c8ee48e9cff
WORKDIR /workspace
COPY requirements.txt /workspace/requirements.txt
RUN pip install -r requirements.txt
COPY . /workspace
CMD ["python", "main.py"]
