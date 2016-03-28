library ieee;
use ieee.std_logic_1164.all;
use ieee.numeric_std.all;

library riscv;
use riscv.pkg_scala2hdl.all;
use riscv.pkg_enum.all;

-- #spinalBegin userLibrary
library STD;
use std.textio.all;
library ieee;
use IEEE.std_logic_textio.all;
library IEEE;
use ieee.math_real.all;
-- #spinalEnd userLibrary


entity Core_tb is
end Core_tb;

architecture arch of Core_tb is
  signal io_i_cmd_valid : std_logic;
  signal io_i_cmd_ready : std_logic;
  signal io_i_cmd_payload_pc : unsigned(31 downto 0);
  signal io_i_rsp_valid : std_logic;
  signal io_i_rsp_ready : std_logic;
  signal io_i_rsp_payload_instruction : std_logic_vector(31 downto 0);
  signal io_d_cmd_valid : std_logic;
  signal io_d_cmd_ready : std_logic;
  signal io_d_cmd_payload_wr : std_logic;
  signal io_d_cmd_payload_address : unsigned(31 downto 0);
  signal io_d_cmd_payload_data : std_logic_vector(31 downto 0);
  signal io_d_cmd_payload_size : unsigned(1 downto 0);
  signal io_d_rsp_valid : std_logic;
  signal io_d_rsp_ready : std_logic;
  signal io_d_rsp_payload : std_logic_vector(31 downto 0);
  signal io_interrupt : std_logic;
  signal clk : std_logic;
  signal reset : std_logic;
  -- #spinalBegin userDeclarations
  constant doTestWithStall : Boolean := true;
  constant doBench : Boolean := true;
  constant doBenchtWithStall : Boolean := true;
  constant doBenchtWithInterrupt : Boolean := true;
  
  
  signal inBench : Boolean := false;
  signal timingRead : std_logic;
  component CoreCH 
    port (
     clk                                  : in   std_logic;
     reset                                : in   std_logic;
     io_host_reset                        : in   std_logic;
     io_host_debug_stats_csr              : out  std_logic;
     io_host_id                           : in   std_logic;
     io_host_csr_req_ready                : out  std_logic;
     io_host_csr_req_valid                : in   std_logic;
     io_host_csr_req_bits_rw              : in   std_logic;
     io_host_csr_req_bits_addr            : in   std_logic_vector(11 downto 0);
     io_host_csr_req_bits_data            : in   std_logic_vector(63 downto 0);
     io_host_csr_rep_ready                : in   std_logic;
     io_host_csr_rep_valid                : out  std_logic;
     io_host_csr_rep_bits                 : out  std_logic_vector(63 downto 0);
     io_host_ipi_req_ready                : in   std_logic;
     io_host_ipi_req_valid                : out  std_logic;
     io_host_ipi_req_bits                 : out  std_logic;
     io_host_ipi_rep_ready                : out  std_logic;
     io_host_ipi_rep_valid                : in   std_logic;
     io_host_ipi_rep_bits                 : in   std_logic;
     io_host_mem_req_valid                : in   std_logic;
     io_host_mem_req_bits_rw              : in   std_logic;
     io_host_mem_req_bits_addr            : in   std_logic_vector(63 downto 0);
     io_host_mem_req_bits_data            : in   std_logic_vector(63 downto 0);
     io_imem_req_ready                    : in   std_logic;
     io_imem_req_valid                    : out  std_logic;
     io_imem_req_bits_addr                : out  std_logic_vector(31 downto 0);
     io_imem_req_bits_fcn                 : out  std_logic;
     io_imem_req_bits_typ                 : out  std_logic_vector(2 downto 0);
     io_imem_resp_valid                   : in   std_logic;
     io_imem_resp_bits_data               : in   std_logic_vector(31 downto 0);
     io_dmem_req_ready                    : in   std_logic;
     io_dmem_req_valid                    : out  std_logic;
     io_dmem_req_bits_addr                : out  std_logic_vector(31 downto 0);
     io_dmem_req_bits_data                : out  std_logic_vector(31 downto 0);
     io_dmem_req_bits_fcn                 : out  std_logic;
     io_dmem_req_bits_typ                 : out  std_logic_vector(2 downto 0);
     io_dmem_resp_valid                   : in   std_logic;
     io_dmem_resp_bits_data               : in   std_logic_vector(31 downto 0)
     );
   end component;
  
  signal io_host_reset                        : std_logic;
  signal io_host_debug_stats_csr              : std_logic;
  signal io_host_id                           : std_logic;
  signal io_host_csr_req_ready                : std_logic;
  signal io_host_csr_req_valid                : std_logic;
  signal io_host_csr_req_bits_rw              : std_logic;
  signal io_host_csr_req_bits_addr            : std_logic_vector(11 downto 0);
  signal io_host_csr_req_bits_data            : std_logic_vector(63 downto 0);
  signal io_host_csr_rep_ready                : std_logic;
  signal io_host_csr_rep_valid                : std_logic;
  signal io_host_csr_rep_bits                 : std_logic_vector(63 downto 0);
  signal io_host_ipi_req_ready                : std_logic;
  signal io_host_ipi_req_valid                : std_logic;
  signal io_host_ipi_req_bits                 : std_logic;
  signal io_host_ipi_rep_ready                : std_logic;
  signal io_host_ipi_rep_valid                : std_logic;
  signal io_host_ipi_rep_bits                 : std_logic;
  signal io_host_mem_req_valid                : std_logic;
  signal io_host_mem_req_bits_rw              : std_logic;
  signal io_host_mem_req_bits_addr            : std_logic_vector(63 downto 0);
  signal io_host_mem_req_bits_data            : std_logic_vector(63 downto 0);
  signal io_imem_req_ready                    : std_logic;
  signal io_imem_req_valid                    : std_logic;
  signal io_imem_req_bits_addr                : std_logic_vector(31 downto 0);
  signal io_imem_req_bits_fcn                 : std_logic;
  signal io_imem_req_bits_typ                 : std_logic_vector(2 downto 0);
  signal io_imem_resp_valid                   : std_logic;
  signal io_imem_resp_bits_data               : std_logic_vector(31 downto 0);
  signal io_dmem_req_ready                    : std_logic;
  signal io_dmem_req_valid                    : std_logic;
  signal io_dmem_req_bits_addr                : std_logic_vector(31 downto 0);
  signal io_dmem_req_bits_data                : std_logic_vector(31 downto 0);
  signal io_dmem_req_bits_fcn                 : std_logic;
  signal io_dmem_req_bits_typ                 : std_logic_vector(2 downto 0);
  signal io_dmem_resp_valid                   : std_logic;
  signal io_dmem_resp_bits_data               : std_logic_vector(31 downto 0);
  
  signal io_d_rsp_buff_valid : std_logic;
  signal io_d_rsp_buff_ready : std_logic;
  signal io_d_rsp_buff_payload : std_logic_vector(31 downto 0);
  
  
  
  signal io_i_cmd_ready_rand : std_logic;
  signal io_d_cmd_ready_rand : std_logic;
  signal io_d_rsp_buff_ready_rand : std_logic;
  signal io_i_rsp_buff_ready_rand : std_logic;

  
  signal io_i_rsp_buff_valid : std_logic;
  signal io_i_rsp_buff_ready : std_logic;
  signal io_i_rsp_buff_payload_instruction : std_logic_vector(31 downto 0);
  
  signal interrupt : std_logic;
  
  shared variable done : integer := 0;
  constant memSize : integer := 1024*256;
  type memType is array (0 to memSize-1) of std_logic_vector(7 downto 0);
  shared variable mem : memType;
  shared variable mem_ref : memType;
  
  signal match_i_cmd_addr : std_logic;
  signal match_d_cmd_addr : std_logic;
  signal match_d_cmd_data : std_logic;
  signal match_i_rsp_data : std_logic;
  signal match_d_rsp_data : std_logic;
  
  signal exp0 : std_logic;
  signal exp1 : std_logic;
  signal exp2 : std_logic;
  signal exp3 : std_logic;

  signal counter : unsigned(31 downto 0);
  function loadHex(path : String) return memType is
    use std.textio.all;
    use ieee.std_logic_textio.all;
    file ROMFILE: TEXT open READ_MODE is path;
    variable newline: line;
    variable newchar: character;
    variable newbyte: std_logic_vector(7 downto 0);
    variable newword: std_logic_vector(15 downto 0);
    variable offset : integer;
    variable NextAddr, ByteCount: integer;
    variable NewROM: memType := (others => (others => '0'));
    variable valid: boolean := True;
  begin
    offset := 0;
    while (valid) loop
        readline(ROMFILE, newline);
        read(newline,newchar,valid);                    
        if (newchar = ':') and (valid = True) then
          hread(newline,newbyte);
          ByteCount := to_integer(unsigned(newbyte));
          hread(newline,newword);
          NextAddr := to_integer(unsigned(newword)) + offset;
          hread(newline,newbyte);
          case(newbyte) is
            when X"00" => 
              for i in 1 to ByteCount loop
                  hread(newline,newbyte);
                  NewROM(NextAddr) := newbyte;
                  NextAddr := NextAddr + 1;
              end loop;        

            when X"02" =>   
              hread(newline,newword);
              offset := to_integer(unsigned(newword)) * (16);
            when others => 
              valid := False;
          end case;
        end if;
    end loop;

    file_close(ROMFILE);
    return NewROM;
  end;
  
  function type_size(that : std_logic_vector(2 downto 0)) return integer is 
  begin
    case to_integer(unsigned(that)) is
      when 0 =>  return 1;
      when 1 =>  return 1;
      when 2 =>  return 2;
      when 3 =>  return 4;
      when 4 =>  return 8;
      when 5 =>  return 1;
      when 6 =>  return 2;
      when 7 =>  return 4;
      when others => 
    end case;
  end function;
  
  function type_signed(that : std_logic_vector(2 downto 0)) return Boolean is 
  begin
    case to_integer(unsigned(that)) is
      when 0 =>  return true;
      when 1 =>  return true;
      when 2 =>  return true;
      when 3 =>  return true;
      when 4 =>  return true;
      when 5 =>  return false;
      when 6 =>  return false;
      when 7 =>  return false;
      when others => 
    end case;
  end function;
    
    
    
  shared variable seed1, seed2: positive;
  impure function randomStdLogic(prob : real) return std_logic is
    variable rand: real;
  begin
    UNIFORM(seed1, seed2, rand);
    if rand < prob then
      return '1';
    else
      return '0';
    end if;
  end randomStdLogic;    
  -- #spinalEnd userDeclarations
begin
  -- #spinalBegin userLogics
  process
  begin
    clk <= '0';
    wait for 5 ns;
    if done = 1 then
      wait;
    end if;
    assert now < 200 ms report "timeout" severity failure;
    clk <= '1';
    wait for 5 ns;
  end process;


  process
    variable testCount,errorCount : integer := 0;
    procedure doTest(path : String;timeoutAt : integer := 10000) is
      variable counter : integer;
    begin
      counter := 0;
      reset <= '1';
      testCount := testCount + 1;
      wait for 1 us;
      wait until rising_edge(clk);
      mem := loadHex(path);
      mem_ref := loadHex(path);
      wait until rising_edge(clk);
      reset <= '0';
      while True loop
        if counter > timeoutAt and timeoutAt /= -1 then
          errorCount := errorCount + 1;
          report "Test fail ! : " & path severity error;  
          exit;
        end if;
        if io_d_cmd_valid = '1' and io_d_cmd_payload_address = X"00000000" then
          if io_d_cmd_payload_data /= X"00000001" then
            errorCount := errorCount + 1;
            report "Test fail ! : " & path severity error;
          end if;
          exit;
        end if;
        wait until rising_edge(clk);
        counter := counter + 1;
      end loop;
    end procedure;
  begin
    reset <= '1';

    wait for 100 ns;

     for i in 0 to 1000 loop
     
      doTest("E:/vm/share/isa/rv32ui-p-mul.hex");   
      doTest("E:/vm/share/isa/rv32ui-p-mulh.hex");   
      doTest("E:/vm/share/isa/rv32ui-p-mulhsu.hex");   
      doTest("E:/vm/share/isa/rv32ui-p-mulhu.hex");   
      doTest("E:/vm/share/isa/rv32ui-p-div.hex");   
      doTest("E:/vm/share/isa/rv32ui-p-divu.hex");   
      doTest("E:/vm/share/isa/rv32ui-p-rem.hex");   
      doTest("E:/vm/share/isa/rv32ui-p-remu.hex");

       --doTest("E:/vm/share/isa/rv32si-p-csr.hex");
       --doTest("E:/vm/share/isa/rv32si-p-illegal.hex");   
       --doTest("E:/vm/share/isa/rv32si-p-ma_addr.hex");   
       --doTest("E:/vm/share/isa/rv32si-p-ma_fetch.hex");   
       --doTest("E:/vm/share/isa/rv32si-p-sbreak.hex");   
       --doTest("E:/vm/share/isa/rv32si-p-scall.hex");   
       --doTest("E:/vm/share/isa/rv32si-p-shamt.hex");   
       doTest("E:/vm/share/isa/rv32ui-p-add.hex");   
       doTest("E:/vm/share/isa/rv32ui-p-addi.hex");   
       --doTest("E:/vm/share/isa/rv32ui-p-amoadd_w.hex");   
       --doTest("E:/vm/share/isa/rv32ui-p-amoand_w.hex");   
       --doTest("E:/vm/share/isa/rv32ui-p-amomaxu_w.hex");   
       --doTest("E:/vm/share/isa/rv32ui-p-amomax_w.hex");   
       --doTest("E:/vm/share/isa/rv32ui-p-amominu_w.hex");   
       --doTest("E:/vm/share/isa/rv32ui-p-amomin_w.hex");   
       --doTest("E:/vm/share/isa/rv32ui-p-amoor_w.hex");   
       --doTest("E:/vm/share/isa/rv32ui-p-amoswap_w.hex");   
       doTest("E:/vm/share/isa/rv32ui-p-and.hex");   
       doTest("E:/vm/share/isa/rv32ui-p-andi.hex");   
       doTest("E:/vm/share/isa/rv32ui-p-auipc.hex");   
       doTest("E:/vm/share/isa/rv32ui-p-beq.hex");   
       doTest("E:/vm/share/isa/rv32ui-p-bge.hex");   
       doTest("E:/vm/share/isa/rv32ui-p-bgeu.hex");   
       doTest("E:/vm/share/isa/rv32ui-p-blt.hex");   
       doTest("E:/vm/share/isa/rv32ui-p-bltu.hex");   
       doTest("E:/vm/share/isa/rv32ui-p-bne.hex");   
      --doTest("E:/vm/share/isa/rv32ui-p-fence_i.hex");   !!! 
       doTest("E:/vm/share/isa/rv32ui-p-j.hex");   
       doTest("E:/vm/share/isa/rv32ui-p-jal.hex");   
       doTest("E:/vm/share/isa/rv32ui-p-jalr.hex");   
       doTest("E:/vm/share/isa/rv32ui-p-lb.hex");   
       doTest("E:/vm/share/isa/rv32ui-p-lbu.hex");   
       doTest("E:/vm/share/isa/rv32ui-p-lh.hex");   
       doTest("E:/vm/share/isa/rv32ui-p-lhu.hex");   
       doTest("E:/vm/share/isa/rv32ui-p-lui.hex");   
       doTest("E:/vm/share/isa/rv32ui-p-lw.hex");     
       doTest("E:/vm/share/isa/rv32ui-p-or.hex");   
       doTest("E:/vm/share/isa/rv32ui-p-ori.hex");    
       doTest("E:/vm/share/isa/rv32ui-p-sb.hex");   
       doTest("E:/vm/share/isa/rv32ui-p-sh.hex");   
       doTest("E:/vm/share/isa/rv32ui-p-simple.hex");   
       doTest("E:/vm/share/isa/rv32ui-p-sll.hex");   
       doTest("E:/vm/share/isa/rv32ui-p-slli.hex");   
       doTest("E:/vm/share/isa/rv32ui-p-slt.hex");   
       doTest("E:/vm/share/isa/rv32ui-p-slti.hex");   
       doTest("E:/vm/share/isa/rv32ui-p-sra.hex");   
       doTest("E:/vm/share/isa/rv32ui-p-srai.hex");   
       doTest("E:/vm/share/isa/rv32ui-p-srl.hex");   
       doTest("E:/vm/share/isa/rv32ui-p-srli.hex");   
       doTest("E:/vm/share/isa/rv32ui-p-sub.hex");   
       doTest("E:/vm/share/isa/rv32ui-p-sw.hex");   
       doTest("E:/vm/share/isa/rv32ui-p-xor.hex");   
       doTest("E:/vm/share/isa/rv32ui-p-xori.hex");   
       wait for 1 us;
       report integer'image(testCount - errorCount) & "/" & integer'image(testCount) & " -> "  & integer'image(errorCount) & " error";
       if doBench then
        exit;
       end if;
     end loop;
     inBench <= true;
     wait for 1 us;
     doTest("E:/vm/share/a.hex",1024*1024*1024);
    


    done := done + 1;
    wait for 1 us;
    report integer'image(testCount - errorCount) & "/" & integer'image(testCount) & " -> "  & integer'image(errorCount) & " error";
    wait;
  end process;

  -- exp0 <= '1' when  io_d_cmd_payload_address = X"00028230" and io_d_cmd_valid = '1' else '0';
  -- exp1 <= '1' when  io_d_cmd_payload_address = X"000224c4" and io_d_cmd_valid = '1' else '0';
  -- match_i_cmd_addr <= '1' when io_i_cmd_payload_address  = unsigned(io_imem_req_bits_addr) else not (io_i_cmd_valid or io_imem_req_valid) ;
  -- match_i_rsp_data <= '1' when io_i_rsp_payload          = io_imem_resp_bits_data else  not (io_i_rsp_valid or io_imem_resp_valid) ;
  -- match_d_cmd_addr <= '1' when io_d_cmd_payload_address  = unsigned(io_dmem_req_bits_addr) else  not (io_d_cmd_valid or io_dmem_req_valid) ;
  -- match_d_cmd_data <= '1' when io_d_cmd_payload_data     = io_dmem_req_bits_data else  not (io_d_cmd_valid or io_dmem_req_valid) ;
  -- match_d_rsp_data <= '1' when io_d_rsp_payload          = io_dmem_resp_bits_data else  not (io_i_rsp_valid or io_dmem_resp_valid) ;
  
  process(clk)
  begin
    if rising_edge(clk) then
      if (inBench and not doBenchtWithStall) or (not inBench and not doTestWithStall) then
        io_d_cmd_ready_rand <= '1';
        io_i_cmd_ready_rand <= '1';
        io_d_rsp_buff_ready_rand <= '1';
        io_i_rsp_buff_ready_rand <= '1';
      else
        io_d_cmd_ready_rand <= randomStdLogic(0.85);
        io_d_rsp_buff_ready_rand <= randomStdLogic(0.85);
        io_i_cmd_ready_rand <= randomStdLogic(0.75);
        io_i_rsp_buff_ready_rand <=  randomStdLogic(0.75);
      end if;
    end if;
  end process;
  
  io_i_cmd_ready <= (not io_i_rsp_buff_valid or io_i_rsp_buff_ready) and io_i_cmd_ready_rand;
  io_d_cmd_ready <= (not io_d_rsp_buff_valid or io_d_rsp_buff_ready) and io_d_cmd_ready_rand;
  io_interrupt <= interrupt;
  process(clk,reset)
    variable char : Character;
    file log : text is out "E:/vm/share/log.txt";
    variable logLine : line;
    variable data : std_logic_vector(31 downto 0);
  begin
    if reset = '1' then
      io_i_rsp_buff_valid <= '0';
      io_d_rsp_buff_valid <= '0';
      counter <= (others => '0');
      timingRead <= '0';
      interrupt <= '0';
    elsif rising_edge(clk) then
      if inBench then
        if interrupt = '0' and doBenchtWithInterrupt then
          interrupt <= randomStdLogic(0.001);
        else
        --  interrupt <= not randomStdLogic(0.1);
        end if;
      else
        interrupt <= '0';
      end if;
      timingRead <= '0';
      counter <= counter + 1;
      if io_i_rsp_buff_ready = '1' then
        io_i_rsp_buff_valid <= '0';
        io_i_rsp_buff_payload_instruction <= (others => 'X');
      end if;
      if io_i_cmd_valid = '1' and io_i_cmd_ready = '1' then
        io_i_rsp_buff_valid <= '1';
        for i in 0 to 3 loop
          data(i*8+7 downto i*8) := mem(to_integer(unsigned(io_i_cmd_payload_pc)) + i);
        end loop;
        if not inBench and io_i_cmd_payload_pc = (31 downto 0 => '0') then
          io_i_rsp_buff_payload_instruction <= X"01c02023";
        elsif data = X"00000073" then
          io_i_rsp_buff_payload_instruction <= X"01c02023";
        elsif data = X"0FF0000F" then
          io_i_rsp_buff_payload_instruction <= X"00000013"; --TODO remove me
        else
          io_i_rsp_buff_payload_instruction <= data;
        end if;
      end if;
      

      if io_d_rsp_buff_ready = '1' then
        io_d_rsp_buff_valid <= '0';
        io_d_rsp_buff_payload <= (others => 'X');
      end if;
      if io_d_cmd_valid = '1' and  io_d_cmd_ready = '1' then
        if io_d_cmd_payload_wr = '1' then
          if io_d_cmd_payload_address = X"10000000" then
            char := character'val(to_integer(unsigned(io_d_cmd_payload_data(7 downto 0))));
            if char /= LF then
              write (logLine, char);
            else
              writeline (log, logLine);
              file_close(log); 
              file_open(log, "E:/vm/share/log.txt", append_mode); 
            end if;
          elsif io_d_cmd_payload_address = X"10000008" then
            interrupt <= io_d_cmd_payload_data(0);
          else
            for i in 0 to (2 ** to_integer(io_d_cmd_payload_size))-1 loop
              mem(to_integer(unsigned(io_d_cmd_payload_address)) + i) := io_d_cmd_payload_data(i*8+7 downto i*8);
            end loop;
          end if;
        else
          io_d_rsp_buff_valid <= '1';
          if io_d_cmd_payload_address = X"10000004" then
            io_d_rsp_buff_payload <= std_logic_vector(counter);
            timingRead <= '1';
          else
            for i in 0 to 3 loop
              io_d_rsp_buff_payload(i*8+7 downto i*8) <= mem(to_integer(unsigned(io_d_cmd_payload_address)) + i);
            end loop;        
          end if;
        end if;
      end if;
    end if;
  end process;
  

  io_i_rsp_buff_ready <= (not io_i_rsp_valid or io_i_rsp_ready) and io_i_rsp_buff_ready_rand;
  process(reset,clk)
  begin
    if reset = '1' then
      io_i_rsp_valid <= '0';
    elsif rising_edge(clk) then
      if io_i_rsp_ready = '1' then
        io_i_rsp_valid <= '0';
        io_i_rsp_payload_instruction <= (others => 'X');
      end if;
      if io_i_rsp_buff_valid = '1' and  io_i_rsp_buff_ready = '1' then
        io_i_rsp_valid <= '1';
        io_i_rsp_payload_instruction <= io_i_rsp_buff_payload_instruction;
      end if;
    end if;
  end process;
  
  -- io_i_rsp_valid <= io_i_rsp_buff_valid;
  -- io_i_rsp_payload_instruction <= io_i_rsp_buff_payload_instruction;
  -- io_i_rsp_buff_ready <=io_i_rsp_ready;

  
  io_d_rsp_buff_ready <= (not io_d_rsp_valid or io_d_rsp_ready) and io_d_rsp_buff_ready_rand;
  process(reset,clk)
  begin
    if reset = '1' then
      io_d_rsp_valid <= '0';
    elsif rising_edge(clk) then
      if io_d_rsp_ready = '1' then
        io_d_rsp_valid <= '0';
        io_d_rsp_payload <= (others => 'X');
      end if;
      if io_d_rsp_buff_valid = '1' and  io_d_rsp_buff_ready = '1' then
        io_d_rsp_valid <= '1';
        io_d_rsp_payload <= io_d_rsp_buff_payload;
      end if;
    end if;
  end process;
  

  io_host_reset                    <= reset;
  io_host_id                       <= '0';
  io_host_csr_req_valid            <= '0';
  io_host_csr_req_bits_rw          <= '0';
  io_host_csr_rep_ready             <= '0';
  io_host_ipi_req_ready              <= '0';
  io_host_ipi_rep_valid                <= '0';
  io_host_mem_req_valid            <= '0';
  io_host_mem_req_bits_rw           <= '0';
  
  
  io_imem_req_ready <= '1';
  io_dmem_req_ready <= '1';
  process(clk,reset)
    variable char : Character;
    file log : text is out "E:/vm/share/log_ref.txt";
    variable logLine : line;
  begin
    if reset = '1' then
      io_imem_resp_valid <= '0';
      io_dmem_resp_valid <= '0';
    elsif rising_edge(clk) then
     -- if io_imem_resp_ready = '1' then
        io_imem_resp_valid <= '0';
        io_imem_resp_bits_data <= (others => 'X');
        if io_imem_req_valid = '1' and io_imem_req_ready = '1' then -- io_imem_req_bits_addr = std_logic_vector(io_i_cmd_payload_address) 
          io_imem_resp_valid <= '1';
          for i in 0 to 3 loop
            io_imem_resp_bits_data(i*8+7 downto i*8) <= mem_ref(to_integer(unsigned(io_imem_req_bits_addr)) + i);
          end loop;
        end if;
    --  end if;
      
      io_dmem_resp_valid <= '0';
      io_dmem_resp_bits_data <= (others => 'X');
      if io_dmem_req_valid = '1' then
        if io_dmem_req_bits_fcn = '1' then
          if io_dmem_req_bits_addr = X"10000000" then
            char := character'val(to_integer(unsigned(io_dmem_req_bits_data(7 downto 0))));
            if char /= LF then
              write (logLine, char);
            else
              writeline (log, logLine);
              file_close(log); 
              file_open(log, "E:/vm/share/log_ref.txt", append_mode); 
            end if;
          else
            for i in 0 to type_size(io_dmem_req_bits_typ)-1 loop
              mem_ref(to_integer(unsigned(io_dmem_req_bits_addr)) + i) := io_dmem_req_bits_data(i*8+7 downto i*8);
            end loop;
          end if;
        else
          io_dmem_resp_valid <= '1';
          if io_dmem_req_bits_addr = X"10000004" then
            io_dmem_resp_bits_data <= std_logic_vector(counter);
          else
            if type_signed(io_dmem_req_bits_typ) then
              case type_size(io_dmem_req_bits_typ) is
                when 1 => io_dmem_resp_bits_data <= (others => mem_ref(to_integer(unsigned(io_dmem_req_bits_addr)))(7));
                when 2 => io_dmem_resp_bits_data <= (others => mem_ref(to_integer(unsigned(io_dmem_req_bits_addr))+1)(7));
                when 4 => 
                when others => report "???" severity error;
              end case;
            else
              io_dmem_resp_bits_data <= (others => '0');
            end if;

            for i in 0 to type_size(io_dmem_req_bits_typ)-1 loop
              if to_integer(unsigned(io_dmem_req_bits_addr)) +  i >= mem_ref'low and to_integer(unsigned(io_dmem_req_bits_addr)) +  i <= mem_ref'high then
                io_dmem_resp_bits_data(i*8+7 downto i*8) <= mem_ref(to_integer(unsigned(io_dmem_req_bits_addr)) + i);
              end if;
            end loop;        
          end if;
        end if;
      end if;
    end if;
  end process;  
  
  -- io_imem_req_ready                    : in   std_logic;
  -- io_imem_req_valid                    : out  std_logic;
  -- io_imem_req_bits_addr                : out  std_logic_vector(31 downto 0);
  -- io_imem_req_bits_fcn                 : out  std_logic;
  -- io_imem_req_bits_typ                 : out  std_logic_vector(2 downto 0);
  -- io_imem_resp_valid                   : in   std_logic;
  -- io_imem_resp_bits_data               : in   std_logic_vector(31 downto 0);
  -- io_dmem_req_ready                    : in   std_logic;
  -- io_dmem_req_valid                    : out  std_logic;
  -- io_dmem_req_bits_addr                : out  std_logic_vector(31 downto 0);
  -- io_dmem_req_bits_data                : out  std_logic_vector(31 downto 0);
  -- io_dmem_req_bits_fcn                 : out  std_logic;
  -- io_dmem_req_bits_typ                 : out  std_logic_vector(2 downto 0);
  -- io_dmem_resp_valid                   : in   std_logic;
  -- io_dmem_resp_bits_data               : in   std_logic_vector(31 downto 0)

  ref : CoreCH  
    port map(
     clk                            => '0'                             ,
     reset                          => '0'                           ,
     io_host_reset                  => io_host_reset                   ,
     io_host_debug_stats_csr        => io_host_debug_stats_csr         ,
     io_host_id                     => io_host_id                      ,
     io_host_csr_req_ready          => io_host_csr_req_ready           ,
     io_host_csr_req_valid          => io_host_csr_req_valid           ,
     io_host_csr_req_bits_rw        => io_host_csr_req_bits_rw         ,
     io_host_csr_req_bits_addr      => io_host_csr_req_bits_addr       ,
     io_host_csr_req_bits_data      => io_host_csr_req_bits_data       ,
     io_host_csr_rep_ready          => io_host_csr_rep_ready           ,
     io_host_csr_rep_valid          => io_host_csr_rep_valid           ,
     io_host_csr_rep_bits           => io_host_csr_rep_bits            ,
     io_host_ipi_req_ready          => io_host_ipi_req_ready           ,
     io_host_ipi_req_valid          => io_host_ipi_req_valid           ,
     io_host_ipi_req_bits           => io_host_ipi_req_bits            ,
     io_host_ipi_rep_ready          => io_host_ipi_rep_ready           ,
     io_host_ipi_rep_valid          => io_host_ipi_rep_valid           ,
     io_host_ipi_rep_bits           => io_host_ipi_rep_bits            ,
     io_host_mem_req_valid          => io_host_mem_req_valid           ,
     io_host_mem_req_bits_rw        => io_host_mem_req_bits_rw         ,
     io_host_mem_req_bits_addr      => io_host_mem_req_bits_addr       ,
     io_host_mem_req_bits_data      => io_host_mem_req_bits_data       ,
     io_imem_req_ready              => io_imem_req_ready               ,
     io_imem_req_valid              => io_imem_req_valid               ,
     io_imem_req_bits_addr          => io_imem_req_bits_addr           ,
     io_imem_req_bits_fcn           => io_imem_req_bits_fcn            ,
     io_imem_req_bits_typ           => io_imem_req_bits_typ            ,
     io_imem_resp_valid             => io_imem_resp_valid              ,
     io_imem_resp_bits_data         => io_imem_resp_bits_data          ,
     io_dmem_req_ready              => io_dmem_req_ready               ,
     io_dmem_req_valid              => io_dmem_req_valid               ,
     io_dmem_req_bits_addr          => io_dmem_req_bits_addr           ,
     io_dmem_req_bits_data          => io_dmem_req_bits_data           ,
     io_dmem_req_bits_fcn           => io_dmem_req_bits_fcn            ,
     io_dmem_req_bits_typ           => io_dmem_req_bits_typ            ,
     io_dmem_resp_valid             => io_dmem_resp_valid              ,
     io_dmem_resp_bits_data         => io_dmem_resp_bits_data      
     );

  -- #spinalEnd userLogics
  uut : entity riscv.Core
    port map (
      io_i_cmd_valid =>  io_i_cmd_valid,
      io_i_cmd_ready =>  io_i_cmd_ready,
      io_i_cmd_payload_pc =>  io_i_cmd_payload_pc,
      io_i_rsp_valid =>  io_i_rsp_valid,
      io_i_rsp_ready =>  io_i_rsp_ready,
      io_i_rsp_payload_instruction =>  io_i_rsp_payload_instruction,
      io_d_cmd_valid =>  io_d_cmd_valid,
      io_d_cmd_ready =>  io_d_cmd_ready,
      io_d_cmd_payload_wr =>  io_d_cmd_payload_wr,
      io_d_cmd_payload_address =>  io_d_cmd_payload_address,
      io_d_cmd_payload_data =>  io_d_cmd_payload_data,
      io_d_cmd_payload_size =>  io_d_cmd_payload_size,
      io_d_rsp_valid =>  io_d_rsp_valid,
      io_d_rsp_ready =>  io_d_rsp_ready,
      io_d_rsp_payload =>  io_d_rsp_payload,
      io_interrupt =>  io_interrupt,
      clk =>  clk,
      reset =>  reset 
    );
end arch;
