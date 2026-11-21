# shooting simulator
공교롭게도 [예비군 시뮬레이터](https://store.steampowered.com/app/5367010/%EC%98%88%EB%B9%84%EA%B5%B0/?l=koreana)가 출시되었지만.. 
참고한 사실은 없음을 알립니다. 
현실적인 사격 환경을 위한 스프라이트 구성 등, 2D이지만 최대한 현장감 있는 사격환경을 구성하는 데에 노력을 많이 들였습니다.

## 실행방법
https://drive.google.com/drive/folders/1sD63MHJ-J5m5PmPThw6pEQ6ZllOqON65?usp=sharing
<br>다운받으신 후 파일압축을 풀고, 
run.bat 을 실행시킵니다.

## 조작방법
wasd로 이동, 쉬프트로 달리기
서서쏴 - 진행도중 마우스 우클릭
앉아쏴 - C키
엎드려쏴 - Z키

## 사격의 팁
사격시 호흡방향에 반대되는 곳으로 마우스를 미세하게 조작해서, 가늠쇠와 가늠자가 정 중앙에 위치할 때 사격하시면 됩니다.

## 각 라운드의 진행
모든 표적지는 초탄 기준으로 탄착군을 형성하셔야 합니다!

1라운드는 표적지에 다섯발 탄착군 형성시 달성됩니다.
2라운드는 첫번째 표적지에 다섯발, 두번째 표적지에 3발 탄착군 형성시 달성됩니다.
추가적으로 탄약이 총 16발 주어지는데, 모두 사격하셔야 라운드가 종료되도록 하였습니다.
3라운드는..
건투를 빕니다.

## Platforms
- `core`: Main module with the application logic shared by all platforms.
- `lwjgl3`: Primary desktop platform using LWJGL3; was called 'desktop' in older docs.

## Gradle

This project uses [Gradle](https://gradle.org/) to manage dependencies.
The Gradle wrapper was included, so you can run Gradle tasks using `gradlew.bat` or `./gradlew` commands.
Useful Gradle tasks and flags:

- `--continue`: when using this flag, errors will not stop the tasks from running.
- `--daemon`: thanks to this flag, Gradle daemon will be used to run chosen tasks.
- `--offline`: when using this flag, cached dependency archives will be used.
- `--refresh-dependencies`: this flag forces validation of all dependencies. Useful for snapshot versions.
- `build`: builds sources and archives of every project.
- `cleanEclipse`: removes Eclipse project data.
- `cleanIdea`: removes IntelliJ project data.
- `clean`: removes `build` folders, which store compiled classes and built archives.
- `eclipse`: generates Eclipse project data.
- `idea`: generates IntelliJ project data.
- `lwjgl3:jar`: builds application's runnable jar, which can be found at `lwjgl3/build/libs`.
- `lwjgl3:run`: starts the application.
- `test`: runs unit tests (if any).

Note that most tasks that are not specific to a single project can be run with `name:` prefix, where the `name` should be replaced with the ID of a specific project.
For example, `core:clean` removes `build` folder only from the `core` project.
