FROM python:3.6 as builder

WORKDIR /app

ARG USERNAME
COPY . /app/

RUN pip install -e '.[dev]' && pip install email_validator

RUN flask adduser -u ${USERNAME} -p 1234
RUN flask addevent -n "Flask Conf" -d "2018-08-25"

FROM qnib/pytest as pytest

WORKDIR /test
COPY --from=builder /app /test
RUN pytest -v --cov-config .coveragerc --cov=talkshow -l --tb=short --maxfail=1 tests/

FROM builder as final

CMD ["flask", "run"]