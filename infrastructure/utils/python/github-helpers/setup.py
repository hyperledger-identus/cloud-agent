"""Setup script for GitHub Helpers"""

from setuptools import find_packages, setup

setup(
    name="github-helpers",
    version="0.1",
    description="GitHub API Python helpers for QA needs.",
    author="Baliasnikov Anton",
    author_email="anton.baliasnikov@iohk.io",
    packages=find_packages(),
    entry_points={
        "console_scripts": ["github=github_helpers.cli:cli"],
    },
    install_requires=[
        "Click==8.1.3",
        "requests==2.32.4",
        "pylint==2.10.2",
        "pytest==6.2.4",
        "pytest-cov==2.12.1",
    ],
)
