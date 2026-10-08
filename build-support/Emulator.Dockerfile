FROM redsquare-android-build:agp8.10.1
USER root
RUN apt-get update && apt-get install -y --no-install-recommends libpulse0 libgl1 libnss3 libasound2 libx11-6 libxcb1 libxkbcommon0 libxcomposite1 libxcursor1 libxi6 libxrandr2 libxtst6 libxdamage1 libglib2.0-0 && rm -rf /var/lib/apt/lists/*
RUN sdkmanager 'emulator' 'system-images;android-29;default;x86_64' 'system-images;android-37.0;google_apis;x86_64'
ENV PATH=${ANDROID_HOME}/emulator:${PATH}
USER gradle
