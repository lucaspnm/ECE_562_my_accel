# ECE_562_my_accel

To run this code in a docker container follow these steps:

1. Set up your env with the following commands:
$ cd /workspace/chipyard
$ source env.sh

------- Compiling the tests -------

2. Transfer schroeder_baseline_test.c & schroeder_accel_test.c to /workspace/chipyard/tests
3. Update CMakelists.txt in the tests/ folder to add executables and dump files:
add_executable(schroeder_baseline_test schroeder_baseline_test.c) # Insert this command to line 90
add_executable(schroeder_accel_test schroeder_accel_test.c) # Insert this command to line 90
add_dump_target(schroeder_baseline_test) # Insert this command to line 129
add_dump_target(schroeder_accel_test) # Insert this command to line 129

3. Execute the following commands:
$ cd /workspace/chipyard/tests
$ mkdir build && cd build
$ cmake .. 
$ make

------- Running the simulations -------

4. Transfer schroeder_RoCC.scala to /workspace/chipyard/src/main/scala/config
5. Execute the following commands:
$ cd /workspace/chipyard/sims/verilator 
$ make CONFIG=SchroederConfig
$ make

To run the baseline simulation:
./simulator-chipyard.harness-RocketConfig /workspace/chipyard/tests/schroeder_baseline.riscv

To run the accelerated simulation:
./simulator-chipyard.harness-SchroederT /workspace/chipyard/tests/schroeder_accel.riscv

note: if input/output terminal print is desired, uncomment the input/output print statements at the end of the file.
