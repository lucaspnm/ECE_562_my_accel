# ECE_562_my_accel

To run this code in a docker container follow these steps:

1. Set-up your env with the following command:
$ cd /workspace/chipyard
$ source env.sh

2. Transfer schroeder_test.c to /workspace/chipyard/tests
FIXME: add target/dump file to CMakeLists.txt
3. Execute the following commands:
$ cd /workspace/chipyard/tests
$ mkdir build && cd build
$ cmake .. 
$ make

note: if input/output terminal print is desired, uncomment the input/output print statements towards the end of the file.

4. Transfer schroeder_RoCC.scala to /workspace/chipyard/sims/verilator
5. Execute the following commands:
$ cd /workspace/chipyard/sims/verilator 
$ make CONFIG=SchroederConfig

To test the workload:
./simulator-chipyard.harness-SchroederConfig /workspace/chipyard/tests/schroederRoCC.riscv

TRASH:

Accelerator:
Upload schroeder.scala file in generators/chipyard/src/main/scala/config
then cd sims/verilator and run ./simulator-chipyard.harness-SchroederConfig /workspace/chipyard/tests/schroederRoCC.riscv

Output should look like this:

=== IR SUMMARY ===
First non-zero index : 0
Last non-zero index  : 1023
Non-zero sample count: 863
Peak amplitude       : 16662 at 0
Done.
