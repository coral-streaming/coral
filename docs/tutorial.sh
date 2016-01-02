sudo kill -9 $(lsof -i:2551 | tail -n1 | awk '{print $2}')

rm -rf /tmp/runtime1.log

java -cp "../coral-runtime-0.0.131.jar" io.coral.api.Boot \
	user add -ccp "192.168.100.101"                       \
	-cp 9042 -k "coral" -n "neo"                          \
	-f "Thomas Anderson" -e "mranderson@metacortex.com"   \
	-m "555-1234" -d "Meta Cortex programming division"

sleep 3

java -cp "../coral-runtime-0.0.131.jar"         \
    io.coral.api.Boot start                     \
	-ai "0.0.0.0" -p 8000 -ah "127.0.0.1"       \
	-ap 2551 -am "coral" -ccp "192.168.100.101" \
	-cp 9042 -k "coral" -nc -ll INFO&

sleep 5
	
curl -H "Content-Type: application/json" \
	-H "Accept: application/json"        \
	--request GET                        \
	--user neo:thematrix                 \
	http://127.0.0.1:8000/api/runtimes
	
curl -H "Content-Type: application/json" \
	-H "Accept: application/json"        \
	--user neo:thematrix                 \
	--request POST                       \
	--data '{ "name": "runtime1", "owner": "neo", "actors": [{ "name": "generator1", "type": "generator", "params": { "format": { "field1": "Hello, world!" }, "timer": { "rate": 1 }}}, { "name": "log1", "type": "log", "params": { "file": "/tmp/runtime1.log" }}], "links": [ { "from": "generator1", "to": "log1" }]}' \
http://127.0.0.1:8000/api/runtimes

sleep 3

curl -H "Content-Type: application/json" \
    -H "Accept: application/json"        \
    --user neo:thematrix                 \
    --request PATCH                      \
    --data '{ "status": "start" }'       \
    http://127.0.0.1:8000/api/runtimes/runtime1
    
sleep 3

tail -f /tmp/runtime1.log