# CS 3251: Programming Assignment 2

File Transfer Application (FxA) over Reliable Transfer Protocol (RxP)

Julia Rapoport, Louis Williams

## FxA

FxA is a simple file transfer application that uses RxP for transport

## RxP

RxP is a byte-stream transport protocol that uses UDP for transmission. It gurantees in-order data delivery, no duplicate data, data integrity, and bi-directional data transfer. It implements a pipelined (windowed) ARQ protocol, where a window size of 1 operates like a stop-and-wait protocol.