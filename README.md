# group

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Made with Python](https://img.shields.io/badge/Made%20with-Python-3776AB.svg)](https://www.python.org/)

A lightweight Python utility for grouping, organizing, and managing collections of data. `group` provides a simple interface for categorizing items based on shared attributes, making it easier to handle large datasets without writing boilerplate iteration logic.

## Features

- **Intuitive API:** Group items quickly with minimal setup.
- **Flexible Keys:** Group by single attributes or custom functions.
- **Zero Dependencies:** Pure standard library implementation with no external requirements.
- **Type Hints:** Fully typed for better IDE support and static analysis.

## Getting Started

### Installation

Clone the repository to your local machine:

```bash
git clone https://github.com/shubhyagami/group.git
cd group
```

### Usage

Import the package and start grouping your data:

```python
from group import group_by

data = [
    {"name": "Alice", "department": "Engineering"},
    {"name": "Bob", "department": "Sales"},
    {"name": "Charlie", "department": "Engineering"},
]

grouped = group_by(data, key=lambda x: x["department"])
print(grouped["Engineering"])
# Output: [{'name': 'Alice', 'department': 'Engineering'}, {'name': 'Charlie', 'department': 'Engineering'}]
```

## Running Tests

To verify everything is working correctly, run the test suite using `pytest`:

```bash
python -m pytest tests/
```

## Changelog

### v1.1.0 - 2026-08-19
- Updated project documentation and README structure.
- Added comprehensive type hints for all public methods.
- Fixed an edge case where empty lists returned `None` instead of an empty dictionary.

## License

This project is licensed under the MIT License. See the `LICENSE` file for details.
