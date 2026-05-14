FROM python:3.14@sha256:fe6ffb340c566fe7459832667da8b545896edb30c3dce0c3b1218ab4d377ba7d
WORKDIR /workspace
COPY requirements.txt /workspace/requirements.txt
RUN pip install -r requirements.txt
COPY . /workspace
CMD ["python", "main.py"]
