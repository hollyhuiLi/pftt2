
# TODO be able to load this file later, to work around extra warnings, etc....
# TODO be able to print a list of all insertions or all insertions contianing 'Warning', etc...
module Tracing
  module Prompt

class Diff < TestCaseRunPrompt
      
  def initialize(dlm)
    @dlm = dlm
    # TODO
  end
      
  def help
    super
    puts ' d  - (or -) delete: modify expect'
    puts ' a  - (or +) add: modify expect'
    puts ' i  - ignore: remove from diffs' # TODO
    puts ' s  - skip line, count'
    puts ' m  - display more commands (this whole list)'
    puts ' t  - Triage diffs in this test'
    puts ' T  - Triage all diffs from all tests'
    puts ' r  - replace expect with regex to match actual'
    puts ' R  - replace all in file'
    puts ' A  - replace all in test case set'
    puts ' l  - show modified expect line (or original if not modified)'
    puts ' L  - show original expect line'
    puts ' P  - show original expect section'
    puts ' p  - show modified expect section (or original if not modified)'
    puts ' v  - show PHPT file format documentation (HTML)'
    puts ' H  - highlight diff (Swing UI)'
    puts ' y  - save chunk replacements to file'
    puts ' Y  - load chunk replacements from a file'
    puts ' k  - save diff to file (insertions and deletions)'
    puts ' K  - save all inserted chunks to file'
  end # def help
      
  def execute(ans)
    if ans=='-' or ans=='d'
      # delete: modify expect
      @dlm.delete
    elsif ans=='+' or ans=='a'
      # add: modify expect
      @dlm.add
    elsif ans=='i'
      # ignore: remove from diffs
      @dlm.ignore
    elsif ans=='s'
      # skip line
      @dlm.skip_line = true
    elsif ans=='r' or ans=='R' or ans=='A'
      # r replace expect with regex to match actual
      # R replace all in file
      # A replace all in test case set
      replace_with = @test_ctx.prompt('Change to(expect_type)') # TODO expect_type
      @dlm.replace(replace_with)
      if ans=='R'
        chunk_replacement[@dlm.chunk] = replace_with
      elsif ans=='A'
        @test_ctx.chunk_replacement[@dlm.chunk] = replace_with
      end
          
      return false # re-prompt
    elsif ans=='t'
      tr = triage()
                
      report = Report::Triage.new(tr)
                
      report.text_print()
                
      return false # re-prompt
    elsif ans=='l'
      # l - show modified expect line (or original if not modified)
      put_line(@dlm.modified_expect_line)
          
      return false # re-prompt
    elsif ans=='L'
      # L - show original expect line
      put_line(@dlm.original_expect_line)
          
      return false # re-prompt
    elsif ans=='E'
      # E - show original expect section
      put_line(@dlm.original_expect_section)
          
      return false # re-prompt
    elsif ans=='e'
      # e - show modified expect section (or original if not modified)
      put_line(@dlm.modified_expect_section)
          
      return false # re-prompt
    elsif ans=='w'
      # w - skip to next host
      @test_ctx.next_host(@host, @middleware, @php, @scn_set)
    elsif ans=='W'
      # W - skip to next host (not interactive)
      $interactive_mode = false
      @test_ctx.next_host(@host, @middleware, @php, @scn_set)
    elsif ans=='v'
      # TODO show PHPT file format documentation (HTML)
                
      return false # re-prompt
    elsif ans=='m'
      puts
      help
      show_expect_info
      puts
      return false # re-prompt
    end
    return super(ans)
  end # def execute
      
end # class Diff

  end
end
