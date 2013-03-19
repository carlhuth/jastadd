/* Copyright (c) 2012, Jesper Öqvist <jesper.oqvist@cs.lth.se>
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 *     * Redistributions of source code must retain the above copyright notice,
 *       this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of the Lund University nor the names of its
 *       contributors may be used to endorse or promote products derived from
 *       this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package org.jastadd;

/**
 * Handles matching and parsing of command line options.
 *
 * An option either takes no value argument or requires a
 * value. If the needsValue boolean is <code>true</code>
 * then the Option will attempt to parse a value to go with it.
 *
 * An option can be made deprecated by calling setDeprecated.
 * This causes a warning to be printed whenever the option is
 * matched.
 *
 * Options are not case sensitive.
 *
 * @author Jesper Öqvist <jesper.oqvist@cs.lth.se>
 */
public class Option {

  /**
   * The prefix string used for all command-line
   * options.
   */
  public static final String OPTION_PREFIX = "--";

  protected String desc;
  protected String name;
  protected String longName;
  protected String longNameLower;
  protected boolean nonStandard = false;
  protected boolean deprecated = false;
  protected boolean matched = false;
  protected String value;
  protected String defaultValue;
  protected boolean needsValue = false;
  protected boolean hasDefaultValue = false;

  /**
   * Create a new standard option without value argument.
   *
   * @param optionName The name of the option
   * @param description The description that will be printed in the help line
   */
  public Option(String optionName, String description) {
    this(optionName, description, false);
  }

  /**
   * Create a new standard option.
   *
   * @param optionName The name of the option
   * @param description The description that will be printed in the help line
   * @param needsValue <code>true</code> if this option requires a value
   */
  public Option(String optionName, String description, boolean needsValue) {
    this(optionName, description, needsValue, false);
  }

  /**
   * Create a new option.
   *
   * @param optionName The name of the option
   * @param description The description that will be printed in the help line
   * @param needsValue <code>true</code> if this option requires a value
   * @param isNonStandard <code>true</code> makes this option a
   * non-standard option
   */
  public Option(String optionName, String description, boolean needsValue,
      boolean isNonStandard) {
    name = optionName;
    longName = OPTION_PREFIX + optionName;
    longNameLower = longName.toLowerCase();
    desc = description;
    nonStandard = isNonStandard;
    value = "";
    this.needsValue = needsValue;
  }

  /**
   * Make this option non-standard.
   */
  public void setNonStandard() {
    nonStandard = true;
  }

  /**
   * Make this option deprecated.
   */
  public void setDeprecated() {
    deprecated = true;
  }

  /**
   * @return <code>true</code> if the option has been matched
   */
  public boolean matched() {
    return matched;
  }

  /**
   * @return The options current value. This is only meaningful
   * to use if matched() returns <code>true</code>.
   */
  public String value() {
    return value;
  }

  /**
   * Set a value for this option.
   * This works just like a default value,  except that
   * it is not printed in the help line for the option.
   * @param v New value
   */
  public void setValue(String v) {
    value = v;
  }

  /**
   * Set a default value for this option
   * @param v The default value
   */
  public void setDefaultValue(String v) {
    defaultValue = v;
    value = v;
    hasDefaultValue = true;
  }

  /**
   * Print help line for this option.
   */
  public void printHelp() {
    int col = 18;
    System.err.print("  " + longName);
    col -= longName.length();
    if (col < 1)
      col = 1;
    for (int i = 0; i < col; ++i)
      System.err.print(' ');
    if (hasDefaultValue) {
      System.err.print(desc);
      System.err.println(" (default = \""
          + defaultValue + "\")");
    } else {
      System.err.println(desc);
    }
  }

  /**
   * Match this option against the argument list.
   *
   * Prints warnings if the same option occurs more than once.
   * Prints warnings when a deprecated option is matched.
   *
   * @param args The argument list array
   * @param index Offset in the argument list to check for a match
   * @return The number of arguments matched.
   * Returns zero if there is no match.
   */
  public int match(String[] args, int index) {
    int offset = 0;
    boolean currentMatch = false;
    if (args[index].toLowerCase().equals(longNameLower)) {
      if (needsValue) {
        if (index < (args.length - 1) && !args[index+1].startsWith(OPTION_PREFIX)) {
          value = args[index+1];
          offset = 2;
        } else {
          System.err.println("Warning: Missing value for option " + name);
          offset = 1;
        }
      }
      currentMatch = true;
      offset = 1;
    } else if (needsValue && args[index].toLowerCase().startsWith(longNameLower + "=")) {
      value = args[index].substring(longNameLower.length() + 1);
      currentMatch = true;
      offset = 1;
    }

    if (currentMatch && matched) {

      System.err.println("Warning: option " + name
          + " occurs more than once in the argument list!");

    } else if (currentMatch && deprecated) {

      System.err.println("Warning: option " + name
          + " has been deprecated! Use of this option is discouraged!");

    }

    matched = currentMatch || matched;

    return offset;
  }
}