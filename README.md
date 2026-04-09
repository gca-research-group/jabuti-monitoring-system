<h1 align="center">
  <br>
  <img src="assets/logo.svg" alt="Jabuti Monitoring System" style="height: 256px">
  <br>
  Jabuti Monitoring System
  <br>
</h1>

<h4 align="center">A framework for executing and monitoring smart contracts</h4>

<p align="center">
  <img alt="Docker" src="https://img.shields.io/badge/Docker-2496ED?style=for-the-badge&logo=docker&logoColor=white" />
  <img alt="Angular" src="https://img.shields.io/badge/Angular-20232f?style=for-the-badge&logo=angular&logoColor=red" />
  <img alt="PostgreSQL" src="https://img.shields.io/badge/PostgreSQL-336791?style=for-the-badge&logo=postgresql&logoColor=white" />
  <img alt="Java" src="https://img.shields.io/badge/Java-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" />
</p>

<br/>

<div align="center">

🚧 **This project is currently under active development.** 🚧  
Expect frequent updates and changes. Your feedback is appreciated!

</div>

---

## Overview

This open-source project aims to serve as middleware between traditional software applications—where business rules reside—and blockchain platforms. It provides a high-level API layer for triggering and monitoring smart contract execution.

---

## Table of Contents

* [Overview](#overview)
* [Architecture](#architecture)
* [How to Run](#how-to-run)
* [Project Repositories](#project-repositories)
* [Related Publications](#related-publications)
* [License](#license)
* [Contact](#contact)

---

## Architecture

<p align="center">
  <img src="assets/architecture.svg" style="width: 100%">
</p>

---

## How to Run

> **Note:** This project is not yet available as a Docker image. For now, it must be run from source. A Docker image will be provided soon for easier setup.

> **Tip for Windows users:** Some shell scripts are provided to simplify the setup. We recommend using the **Git Bash** terminal for compatibility.

### Prerequisites

* Docker
* Node.js v22 or higher
* Java v25 or higher

### Setup Steps

1. **Clone the repository**

```sh
git clone https://github.com/gca-research-group/smart-contract-execution-monitoring-system.git
```

2. **Set up environment variables**

   * Provide values for MongoDB, PostgreSQL, and RabbitMQ.
   * Configuration files are located in `.docker/scems/env/`.
   * Use the example files as templates.
   * Optionally, run the script `.scripts/scems/envs/up.sh` to auto-fill the variables.

3. **Run required services**

```sh

# PostgreSQL
./.scripts/scems/postgres/up.sh

# RabbitMQ
./.scripts/scems/rabbitmq/up.sh
```

4. **Run the frontend**

```sh
cd web
npm install
npm run start
```

5. **Run the backend**

> **Note:** Under maintenance.

6. **Access the application**

Open [https://localhost:4200](https://localhost:4200) in your browser.
Default credentials:

* **Email**: `admin@admin.com`
* **Password**: `admin`

---

## Project Repositories

- [Jabuti Monitoring System](https://github.com/gca-research-group/jabuti-monitoring-system)
- [Fabric Network Orchestrator](https://github.com/gca-research-group/fabric-network-orchestrator)
- [Transformation Engine](https://github.com/gca-research-group/jabuti-ce-transformation-engine)
- [Jabuti CE (VSCode Plug-in)](https://github.com/gca-research-group/jabuti-ce-vscode-plugin)
- [Jabuti DSL Grammar](https://github.com/gca-research-group/jabuti-ce-jabuti-dsl-grammar)
- [Jabuti XText/Xtend implementation](https://github.com/gca-research-group/dsl-smart-contract-eai)

## Related Publications

- 2025
  - [Proposing a Tool to Monitor Smart Contract Execution in Integration Processes](https://sol.sbc.org.br/index.php/sbsi_estendido/article/view/34617)
  - [Towards a Smart Contract Toolkit for Application Integration](#)
 
- 2024
  - [Jabuti CE: A Tool for Specifying Smart Contracts in the Domain of Enterprise Application Integration](https://www.scitepress.org/Link.aspx?doi=10.5220/0012413300003645)

- 2022
  - [Advances in a DSL to Specify Smart Contracts for Application Integration Processes](https://sol.sbc.org.br/index.php/cibse/article/view/20962)
  - [On the Need to Use Smart Contracts in Enterprise Application Integration](https://idus.us.es/handle/11441/140199)

---

## License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.

---

## Contact

If you have any questions or issues, feel free to [open an issue](https://github.com/gca-research-group/smart-contract-execution-monitoring-system/issues) or contact the maintainers.
