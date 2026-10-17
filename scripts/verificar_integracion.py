"""Ejecuta las pruebas contra RabbitMQ real sin publicar AMQP en el host.

Requiere .env de Rabbit Admin e imagen de pruebas construida. Lee credenciales de
Infra solamente en memoria para comparar la topologia protegida antes/despues.
"""
import argparse
import base64
import json
from pathlib import Path
import subprocess
import urllib.request

ROOT = Path(__file__).resolve().parent.parent


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--infra", type=Path, default=ROOT.parent / "digitalfix-infra")
    args = parser.parse_args()
    config = json.loads(subprocess.check_output(["docker","compose","--env-file",str(args.infra/"mq/.env"),
        "-f",str(args.infra/"mq/compose.yml"),"config","--format","json"]))
    admin = config["services"]["rabbitmq1"]["environment"]
    auth = base64.b64encode((admin["RABBITMQ_DEFAULT_USER"]+":"+admin["RABBITMQ_DEFAULT_PASS"]).encode()).decode()

    def leer(path):
        request = urllib.request.Request("http://127.0.0.1:15672/api/"+path,headers={"Authorization":"Basic "+auth})
        with urllib.request.urlopen(request,timeout=15) as response:
            return json.load(response)

    def protegido(name):
        return name.startswith(("cmd.","q.cmd."))

    def snapshot():
        colas = [{k:q.get(k) for k in ("name","durable","auto_delete","arguments","node")}
                 for q in leer("queues/digitalfix") if protegido(q["name"])]
        exchanges = [{k:e.get(k) for k in ("name","type","durable","auto_delete","internal","arguments")}
                     for e in leer("exchanges/digitalfix") if protegido(e["name"])]
        bindings = [b for b in leer("bindings/digitalfix") if protegido(b["source"]) or protegido(b["destination"])]
        return {"colas":sorted(colas,key=lambda q:q["name"]),"exchanges":sorted(exchanges,key=lambda e:e["name"]),
                "bindings":sorted(bindings,key=lambda b:json.dumps(b,sort_keys=True))}

    antes = snapshot()
    output = ROOT / "evidencias-locales"
    output.mkdir(exist_ok=True)
    with (output/"integracion.log").open("w",encoding="utf-8") as log:
        result = subprocess.run(["docker","run","--rm","--network","digitalfix-messaging",
            "--env-file",str(ROOT/".env"),"-e","RABBITMQ_TEST_ADDRESSES=rabbitmq1:5672,rabbitmq2:5672",
            "digitalfix-rabbit-admin:pruebas"],stdout=log,stderr=subprocess.STDOUT,timeout=240)
    despues = snapshot()
    (output/"topologia-protegida.json").write_text(json.dumps(despues,indent=2,ensure_ascii=False),encoding="utf-8")
    if antes != despues:
        raise RuntimeError("La topologia protegida cambio; revisar antes de continuar")
    print("OK: topologia funcional de Notify/Report sin cambios")
    for line in (output/"integracion.log").read_text(encoding="utf-8").splitlines():
        if "Tests run:" in line or "BUILD " in line:
            print(line)
    if result.returncode:
        raise SystemExit("Pruebas fallidas: revisar evidencias-locales/integracion.log")
    print("OK: pruebas con broker real y JWT firmados locales (no autenticacion real Entra)")


if __name__ == "__main__":
    main()
