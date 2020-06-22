# Brainclass
A tiny and fast brainfuck to java bytecode converter written in less than 200 lines (excluding libraries).
## Usage
`java -jar brainclass.jar your-bf-program.b`
## Details
| Variable      | Value   | 
| ------------- |:-------:| 
| Cell size     | 16-bit  |
| Pointer range | 0-65535 | 

This only works with programs that output something. The library that is used to generate bytecode is ASM.
