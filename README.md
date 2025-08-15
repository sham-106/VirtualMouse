# VirtualMouse (Java + OpenCV)

A virtual mouse implementation using **OpenCV** for color-based fingertip tracking and **Java Robot API** for controlling mouse movements and clicks.

Move your index finger (blue marker) to control the cursor and bring your thumb (red marker) close to click — all without touching the mouse! 

---

## Demo

---

##  Features
- Real-time camera feed with fingertip tracking
- Smooth cursor movement using a smoothing factor
- Color-based detection with configurable HSV ranges
- Click detection when index finger & thumb meet
- Single preview window using Swing for UI

---

##  Dependencies
- **Java 8+**
- **Maven** (for building the project)
- **OpenCV 4.x** (Java bindings)

### Installing OpenCV
1. Download OpenCV from: [https://opencv.org/releases/](https://opencv.org/releases/)  
2. Extract it somewhere on your system.
3. Add the `.jar` file to your project:
   - Usually located in: `opencv/build/java/opencv-xxx.jar`
4. Make sure the native library (`opencv_javaxxx.dll` on Windows, `.so` on Linux, `.dylib` on Mac) is in your system path.
5. In IntelliJ IDEA:
   - File → Project Structure → Libraries → Add the OpenCV jar
   - Set `-Djava.library.path` to point to the folder containing the `.dll`/`.so` file.

---

##  How to Run
1. Clone this repository:
   ```bash
   git clone https://github.com/<your-username>/VirtualMouse.git
   cd VirtualMouse
2. Install dependencies:
   ```
   mvn clean install
4. Run the project:
   ```
   mvn exec:java -Dexec.mainClass="me.shambhavi.Main"

   
## By default

Blue marker → Index finger (cursor control)

Red marker → Thumb (click action)

You can tweak the HSV values in Main.java for better detection under different lighting.


