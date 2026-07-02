<h1 align="center">
  Jabuti Monitoring System Experiments
  <br>
</h1>

<div align="center">

🚧 **This project is currently under development.** 🚧  
Expect frequent updates and changes. Your feedback is appreciated!

</div>

## Overview

This project centralizes the experiments that evaluate the [Fabric Network Orchestrator](https://github.com/gca-research-group/hyperledger-fabric-development-network-manager) and the [Jabuti Monitoring System](https://github.com/gca-research-group/jabuti-monitoring-system). It automates the execution of various benchmark scenarios to measure the performance and scalability of Jabuti Monitoring System.

## Table of contents

- [Overview](#overview)
- [Project Structure](#project-structure)
- [Getting Started](#getting-started)
  - [Prerequisites](#prerequisites)
  - [Installation](#installation)
  - [Configuration](#configuration)
  - [Running the Experiments](#running-the-experiments)
- [Project repositories](#project-repositories)
- [Related Publications](#related-publications)
- [License](#license)
- [Contact](#contact)

## Project Structure

- `cmd/experiments/main.go`: The main entry point that orchestrates the execution of various scenarios.
- `internal/api/`: Contains the HTTP client used to interact with the benchmark API and execute smart contracts.
- `internal/config/`: Handles configuration loading from environment variables and `.env` files.
- `internal/report/`: Provides functionality to save experiment metadata and results (e.g., `scenarios.csv`).
- `internal/runner/`: Contains the core logic for generating scenarios and managing parallel execution with randomized event intervals.

## Getting Started

### Prerequisites

- **Go** (version 1.26 or higher)

### Installation

1. Clone the repository:
   ```bash
   git clone https://github.com/gca-research-group/jabuti-monitoring-system-experiments
   ```

2. Install the dependencies:
   ```bash
   go mod tidy
   ```

### Configuration

Before running the experiments, you need to configure the environment variables. You can create a `.env` file based on the provided example:

```bash
cp .env.example .env
```

Edit the `.env` file with your specific settings:

- `API_BASE_URL`: The URL of the benchmark API.
- `ADMIN_EMAIL`: Admin email for authentication.
- `ADMIN_PASSWORD`: Admin password for authentication.
- `BLOCKCHAIN_ID`: The ID of the blockchain network to use.
- `SMART_CONTRACT_ID`: The ID of the smart contract to execute.

### Running the Experiments

To start the experiment suite, run the following command:

```bash
go run cmd/experiments/main.go
```

The system will:
1. Generate a series of scenarios with varying parameters (events, parallels, consumers).
2. Save the generated scenarios to `scenarios.csv`.
3. Sequentially execute each scenario, logging the progress to the console.

## Project repositories

- [Jabuti Monitoring System](https://github.com/gca-research-group/jabuti-monitoring-system)
- [Fabric Network Orchestrator](https://github.com/gca-research-group/hyperledger-fabric-development-network-manager)
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

## License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.

## Contact

For any questions or issues, please open an issue on GitHub or contact the maintainers.
