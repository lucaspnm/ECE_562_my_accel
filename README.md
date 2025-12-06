# ECE_562_my_accel

Note: These instructions assume a similar environment to the Docker containter that was provided by the TA

1. Set up your env with the following commands:
$ cd /workspace/chipyard
$ source env.sh

------- Compiling the tests -------

2. Transfer reverb.h, schroeder_baseline.c, and schroeder_accel.c to /workspace/chipyard/tests
3. Update CMakelists.txt in the tests/ folder to add executables and dump files:
add_executable(schroeder_baseline_test schroeder_baseline_test.c) # Insert this command to line 90
add_executable(schroeder_accel_test schroeder_accel_test.c) # Insert this command to line 90
add_dump_target(schroeder_baseline_test) # Insert this command to line 129
add_dump_target(schroeder_accel_test) # Insert this command to line 129

4. Execute the following commands:
$ cd /workspace/chipyard/tests
$ mkdir build && cd build
$ cmake .. 
$ make

------- Running the simulations -------

5. Transfer schroeder_RoCC.scala to /workspace/chipyard/src/main/scala/config
6. Execute the following commands:
$ cd /workspace/chipyard/sims/verilator 
$ make CONFIG=SchroederConfig
$ make

7. To run the baseline simulation:
./simulator-chipyard.harness-RocketConfig /workspace/chipyard/tests/schroeder_baseline.riscv

8. To run the accelerated simulation:
./simulator-chipyard.harness-SchroederConfig /workspace/chipyard/tests/schroeder_accel.riscv

Note: if input/output terminal print is desired, uncomment the input/output print statements at the end of the file.
