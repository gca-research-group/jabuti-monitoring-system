package runner

import (
	"log"
	"os"

	"golang.org/x/crypto/ssh"
)

func runCommand(client *ssh.Client, command string) ([]byte, error) {
	session, err := client.NewSession()
	if err != nil {
		return nil, err
	}
	defer session.Close()

	return session.CombinedOutput(command)
}

func connect(address string) (*ssh.Client, error) {
	privateKey, err := os.ReadFile("C:\\Users\\teles\\.ssh\\id_ed25519")
	if err != nil {
		log.Fatal(err)
	}

	signer, err := ssh.ParsePrivateKey(privateKey)
	if err != nil {
		log.Fatal(err)
	}

	config := &ssh.ClientConfig{
		User: "root",
		Auth: []ssh.AuthMethod{
			ssh.PublicKeys(signer),
		},
		HostKeyCallback: ssh.InsecureIgnoreHostKey(),
	}

	return ssh.Dial("tcp", address, config)
}

func HyperledgerFabric() {
	client, err := connect("200.17.87.154:22")

	if err != nil {
		log.Fatal(err)
	}

	defer client.Close()

	_, err = runCommand(client, "cd /home/monitor/app && fno --config network-with-chaincode.yml network down")
	if err != nil {
		log.Fatal(err)
	}

	_, err = runCommand(client, "rm -rf /home/monitor/app/output/network-with-chaincode")
	if err != nil {
		log.Fatal(err)
	}

	_, err = runCommand(client, "cp -a /home/monitor/app/baseline/network-with-chaincode /home/monitor/app/output/network-with-chaincode")
	if err != nil {
		log.Fatal(err)
	}

	_, err = runCommand(client, "cd /home/monitor/app && fno --config network-with-chaincode.yml network up")
	if err != nil {
		log.Fatal(err)
	}
}

func RabbitMQ() {
	client, err := connect("200.17.87.130:22")

	if err != nil {
		log.Fatal(err)
	}

	defer client.Close()

	//down
	_, err = runCommand(client, "cd /home/monitor/app && docker compose down")
	if err != nil {
		log.Fatal(err)
	}

	//clean
	_, err = runCommand(client, "cd /home/monitor/app && rm -rf volumes/rabbitmq")
	if err != nil {
		log.Fatal(err)
	}

	//copy
	_, err = runCommand(client, "cd /home/monitor/app && cp -a volumes/baseline volumes/rabbitmq")
	if err != nil {
		log.Fatal(err)
	}

	//up
	_, err = runCommand(client, "cd /home/monitor/app && docker compose up --build -d")
	if err != nil {
		log.Fatal(err)
	}
}

func Postgres() {
	client, err := connect("200.17.87.134:22")

	if err != nil {
		log.Fatal(err)
	}

	defer client.Close()

	//down
	_, err = runCommand(client, "cd /home/monitor/app && docker compose -f network.yml -f postgres.yml down")
	if err != nil {
		log.Fatal(err)
	}

	//clean
	_, err = runCommand(client, "cd /home/monitor/app && rm -rf volumes/postgres/data")
	if err != nil {
		log.Fatal(err)
	}

	//copy
	_, err = runCommand(client, "cd /home/monitor/app && cp -a volumes/postgres/baseline volumes/postgres/data")
	if err != nil {
		log.Fatal(err)
	}

	//copy
	_, err = runCommand(client, "cd /home/monitor/app && cp -a volumes/postgres/baseline volumes/postgres/data")
	if err != nil {
		log.Fatal(err)
	}

	//up
	_, err = runCommand(client, "cd /home/monitor/app && docker compose -f network.yml -f postgres.yml up --build -d")
	if err != nil {
		log.Fatal(err)
	}
}

func Api() {
	client, err := connect("200.17.87.137:22")

	if err != nil {
		log.Fatal(err)
	}

	defer client.Close()

	//down
	_, err = runCommand(client, "cd /var/www/app/api && docker compose -f api.yml -f node-exporter.yml -f nginx-exporter.yml down")
	if err != nil {
		log.Fatal(err)
	}

	//up
	_, err = runCommand(client, "cd /var/www/app/api && docker compose -f api.yml -f node-exporter.yml -f nginx-exporter.yml up --build -d")
	if err != nil {
		log.Fatal(err)
	}
}
