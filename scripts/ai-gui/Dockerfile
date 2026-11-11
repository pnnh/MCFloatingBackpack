FROM eclipse-temurin:21-jdk-jammy
ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get update && apt-get install -y --no-install-recommends \
    xvfb xdotool imagemagick x11-utils x11-xserver-utils mesa-utils \
    libgl1-mesa-dri libglx-mesa0 libglu1-mesa libopenal1 \
    libxrandr2 libxcursor1 libxi6 libxxf86vm1 libxrender1 libxtst6 libxinerama1 libxkbcommon0 \
    fonts-dejavu-core ca-certificates git unzip \
 && rm -rf /var/lib/apt/lists/*
ENV DISPLAY=:99 LIBGL_ALWAYS_SOFTWARE=1 GALLIUM_DRIVER=llvmpipe \
    MESA_GL_VERSION_OVERRIDE=4.5 MESA_GLSL_VERSION_OVERRIDE=450
WORKDIR /work
