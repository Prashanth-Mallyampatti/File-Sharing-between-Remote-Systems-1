# Selective-Repeat-ARQ

This project implements Selective-Repeat-ARQ protocol with a probability drop factor of `'p'` and on port `7735`. 

Run `exec.sh` to compile the client and server programs. 

To run Server (example): 
    
    java Server <port number> <file name> <probability>
    
    java Server 7735 servertest.txt 0.05

To run Client (example):

    java Client <host name or address> <port number> <test file> <N> <MSS>
    
    java Client localhost 7735 test.txt 64 500
