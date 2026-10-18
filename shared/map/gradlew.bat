@rem Copyright 2026 The Ground Authors.
@rem
@rem Licensed under the Apache License, Version 2.0 (the "License");
@rem you may not use this file except in compliance with the License.
@rem You may obtain a copy of the License at
@rem
@rem     https://www.apache.org/licenses/LICENSE-2.0
@rem
@rem Unless required by applicable law or agreed to in writing, software
@rem distributed under the License is distributed on an "AS IS" BASIS,
@rem WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
@rem See the License for the specific language governing permissions and
@rem limitations under the License.

@rem Runs this build with the Gradle wrapper of shared/core, so the repository doesn't need another
@rem copy of the wrapper JAR.
@echo off
call "%~dp0..\core\gradlew.bat" -p "%~dp0." %*
