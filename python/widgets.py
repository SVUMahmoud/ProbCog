# widgets module for use with MLN tools
#
# (C) 2006-2011 by Dominik Jain
#
# Permission is hereby granted, free of charge, to any person obtaining
# a copy of this software and associated documentation files (the
# "Software"), to deal in the Software without restriction, including
# without limitation the rights to use, copy, modify, merge, publish,
# distribute, sublicense, and/or sell copies of the Software, and to
# permit persons to whom the Software is furnished to do so, subject to
# the following conditions:
#
# The above copyright notice and this permission notice shall be
# included in all copies or substantial portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
# EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
# MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
# IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
# CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
# TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
# SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

from Tkinter import *
from ScrolledText import ScrolledText
from string import ascii_letters, digits, punctuation
try:
    import Pmw
    havePMW = True
except:
    havePMW = False
import os
from fnmatch import fnmatch
#import keyword

class ScrolledText2(ScrolledText):
    def __init__(self, root, change_hook = None):
        ScrolledText.__init__(self,root,wrap=NONE,bd=0,width=80,height=25,undo=1,maxundo=50,padx=0,pady=0,background="white",foreground="black")

class Highlighter(object):
    def __init__(self):
        # syntax highlighting definitions
        self.tags = {
                'com': dict(foreground='#aaa'), # comment
                'mlcom': dict(foreground='#aaa'), # multi-line comment
                'str': dict(foreground='darkcyan'), # string
                'kw': dict(foreground='blue'), # keyword
                'obj': dict(foreground='#00F'), # function/class name
                'number': dict(foreground='darkred'), # number
                'op' : dict(foreground='blue'), # operator
                'bracket_hl': dict(background="yellow") # bracket highlighting
                }
        self.brackets = (('(',')'), ('{', '}'))
        self.open_brackets = map(lambda x: x[0], self.brackets)
        self.close_brackets = map(lambda x: x[1], self.brackets)
        self.operators = ['v', '^', '!', '+', '=>', '<=>']
        self.keywords = [] #keyword.kwlist

class BLNHighlighter(Highlighter):
    def __init__(self):
        Highlighter.__init__(self)
        self.keywords = ["type", "Type", "fragments", "isa", "random", "logical", "relationKey", "constraints", "guaranteed", "combining-rule", "uniform-default", "prolog"]

class SyntaxHighlightingText(ScrolledText2):

    # constructor
    def __init__(self, root, change_hook = None, highlighter = None):
        ScrolledText2.__init__(self,root,change_hook)
        # Non-wrapping, no border, undo turned on, max undo 50
        self.text = self # For the methods taken from IDLE
        self.root = root
        self.change_hook = change_hook
        self.characters = ascii_letters + digits + punctuation
        self.tabwidth = 8    # for IDLE use, must remain 8 until Tk is fixed
        self.indentwidth = 4
        self.indention = 0   # The current indention level
        self.set_tabwidth(self.indentwidth) # IDLE...
        self.previous_line = "0"

        # create a popup menu
        self.menu = Menu(root, tearoff=0)
        self.menu.add_command(label="Undo", command=self.edit_undo)
        self.menu.add_command(label="Redo", command=self.edit_redo)
        #self.menu.add_command(type="separator")
        self.menu.add_command(label="Cut", command=self.cut)
        self.menu.add_command(label="Copy", command=self.copy)
        self.menu.add_command(label="Paste", command=self.paste)

        self.bind('<KeyRelease>', self.key_release)      # For scanning input
        self.bind('<Return>',self.autoindent)   # Overides default binding
        #self.bind('<Tab>',self.autoindent) # increments self.indention
        #self.bind('<BackSpace>',self.autoindent) # decrements self.indention
        self.bind('<Button-3>', self.popup) # right mouse button opens popup
        self.bind('<Button-1>', self.recolorCurrentLine) # left mouse can reposition cursor, so recolor (e.g. bracket highlighting necessary)
        self.bind('<Control-Any-KeyPress>', self.ctrl)

        self.setHighlighter(highlighter)

    def setHighlighter(self, highlighter):
        if highlighter == None:
            highlighter = Highlighter()
        self.highlighter = highlighter
        # sets up the tags
        for tag, settings in self.highlighter.tags.items():
            self.tag_config(tag, **settings)

    def popup(self, event):
        self.menu.post(event.x_root, event.y_root)

    def get_tabwidth(self):
        # From IDLE
        current = self['tabs'] or 5000
        return int(current)

    def set_tabwidth(self, newtabwidth):
        # From IDLE
        text = self
        if self.get_tabwidth() != newtabwidth:
            pixels = text.tk.call("font", "measure", text["font"],
                                  "-displayof", text.master,
                                  "n" * newtabwidth)
            text.configure(tabs=pixels)

    def remove_singleline_tags(self, start, end):
        for tag in self.highlighter.tags.keys():
            if tag[:2] != 'ml':
                self.tag_remove(tag, start, end)

    def get_selection_indices(self):
         # If a selection is defined in the text widget, return (start,
        # end) as Tkinter text indices, otherwise return (None, None)
        try:
            first = self.text.index("sel.first")
            last = self.text.index("sel.last")
            return first, last
        except TclError:
            return None

    def cut(self,event=0):
        self.clipboard_clear()
        Selection=self.get_selection_indices()
        if Selection is not None:
            SelectedText = self.get(Selection[0],Selection[1])
            self.delete(Selection[0],Selection[1])
            self.clipboard_append(SelectedText)
            self.onChange()

    def copy(self,event=0):
        self.clipboard_clear()
        Selection=self.get_selection_indices()
        if Selection is not None:
            SelectedText = self.get(Selection[0],Selection[1])
            self.clipboard_append(SelectedText)

    def paste(self,event=0):
        # This should call colorize for the pasted lines.
        SelectedText = self.root.selection_get(selection='CLIPBOARD')
        self.insert(INSERT, SelectedText)
        self.onChange()
        return "break"

    def autoindent(self,event):
        if event.keysym == 'Return':
            self.edit_separator() # For undo/redo
            index = self.index(INSERT).split('.')
            #print index
            line = int(index[0])
            column = int(index[1])
            if self.get('%s.%d'%(line, column-1)) == ':':
                self.indention += 1
            #print '\n',
            #print '\t'*self.indention
            self.insert(INSERT,'\n')
            self.insert(INSERT,'\t'*self.indention)
            return 'break' # Overides standard bindings
        elif event.keysym == 'Tab':
            self.edit_separator()
            self.indention += 1
            #print self.indention
        elif event.keysym == 'BackSpace':
            self.edit_separator()
            index = self.index(INSERT).split('.')
            #print index
            line = int(index[0])
            column = int(index[1])
            if self.get('%s.%d'%(line, column-1)) == '\t':
                self.indention -= 1

    def recolorCurrentLine(self, *foo):
        pos = self.index(INSERT)
        cline = pos.split('.')[0]
        #print "recoloring %s, %s" % (cline, self.previous_line)
        if cline != self.previous_line: self.colorize(self.previous_line)
        self.colorize(cline)
        self.previous_line = cline

    def key_release(self, key):
        #print "pressed", key.keysym, dir(key)
        if key.char in ' :[(]),"\'':
            self.edit_separator() # For undo/redo
        # recolorize the current line and the previous line (if it's a different one)
        self.recolorCurrentLine()
        # if delete or backspace were pressed, check if a multiline comment has to be removed
        pos = self.index(INSERT)
        if key.keysym in ("BackSpace", "Delete"):
            #print "removal at %s" % pos
            ranges = self.tag_ranges('mlcom')
            i = 0
            while i < len(ranges):
                range = ranges[i:i+2]
                second_range = (self.index(str(range[0]) + " + 1 char"), self.index(str(range[1]) + " - 1 char"))
                #print pos, range, second_range
                if pos in range or pos in second_range:
                    self.tag_remove('mlcom', range[0], range[1])
                i += 2
        # notify of change if any
        if key.char != '' or key.keysym in ("BackSpace", "Delete"):
            self.onChange()
        else:
            pass
            #print key

    def onChange(self):
        if self.change_hook is not None:
            self.change_hook()

    def ctrl(self, key):
        #if key.keysym == 'c': self.copy()
        #elif key.keysym == 'x': self.cut()
        #elif key.keysym == 'v': self.paste()
        pass # apparently now implemented in the control itself

    def colorize(self, cline):
        cursorPos = self.index(INSERT)
        buffer = self.get('%s.%d' % (cline,0), '%s.end' % (cline))

        # remove non-multiline tags
        self.remove_singleline_tags('%s.%d'% (cline, 0), '%s.end'% (cline))

        in_quote = False
        quote_start = 0
        for i in range(len(buffer)):
            here = '%s.%d' % (cline, i)
            # strings
            if buffer[i] in ['"',"'"]: # Doesn't distinguish between single and double quotes...
                if in_quote:
                    self.tag_add('str', '%s.%d' % (cline, quote_start), '%s.%d' % (cline, i+1))
                    in_quote = False
                else:
                    quote_start = i
                    in_quote = True
            if not in_quote:
                # operators
                if False:
                    for op in self.highlighter.operators:
                        if buffer[i:i+len(op)] == op:
                            self.tag_add('op', "%s.%d" % (cline, i), "%s.%d" % (cline, i+len(op)))
                # comments
                if buffer[i:i+2] == "//":
                    self.tag_add('com', '%s.%d' % (cline, i), '%s.end' % (cline))
                # multiline comments
                elif buffer[i:i+2] == "/*":
                    if not here in self.tag_ranges('mlcom'):
                        end_pos = self.search("*/", here, forwards=True) # get the end of the comment
                        if not end_pos:
                            continue
                        if self.search("/*", here + " + 2 chars", stopindex=end_pos): # if there's a nested comment, ignore it (it might just be a nested /* with a */)
                            continue
                        #!!! make sure the area does not contain any "/*", because the "*/" is not the right one otherwise
                        #print "multiline comment from %s to %s" % (here, str(end_pos))
                        self.tag_add('mlcom', here, str(end_pos) + " + 2 chars")
                elif buffer[i:i+2] == "*/":
                    end_pos = self.index(here + " + 2 chars")
                    if not end_pos in self.tag_ranges('mlcom'):
                        start_pos = self.search("/*", here, backwards=True) # get the beginning of the comment
                        if not start_pos:
                            continue
                        if self.search("*/", here, stopindex=start_pos, backwards=True): # if there's a nested comment, ignore it (it might just be a nested */ without a /*)
                            continue
                        #print "multiline comment from %s to %s" % (start_pos, end_pos)
                        self.tag_add('mlcom', start_pos, end_pos)
                # bracket highlighting
                elif buffer[i] in self.highlighter.open_brackets and here == cursorPos:
                    idxBracketType = self.highlighter.open_brackets.index(buffer[i])
                    openb, closeb = self.highlighter.brackets[idxBracketType]
                    stack = 1
                    for j,c in enumerate(buffer[i+1:]):
                        if c == openb:
                            stack += 1
                        elif c == closeb:
                            stack -= 1
                            if stack == 0:
                                self.tag_add('bracket_hl', here, here + " + 1 char")
                                self.tag_add('bracket_hl', "%s.%d" % (cline, i+1+j), "%s.%d" % (cline, i+1+j+1))
                                break
                elif buffer[i] in self.highlighter.close_brackets and self.index(here + " + 1 char") == cursorPos:
                    idxBracketType = self.highlighter.close_brackets.index(buffer[i])
                    openb, closeb = self.highlighter.brackets[idxBracketType]
                    stack = 1
                    l = list(buffer[:i])
                    l.reverse()
                    for j,c in enumerate(l):
                        if c == closeb:
                            stack += 1
                        elif c == openb:
                            stack -= 1
                            if stack == 0:
                                self.tag_add('bracket_hl', here, here + " + 1 char")
                                self.tag_add('bracket_hl', "%s.%d" % (cline, i-1-j), "%s.%d" % (cline, i-1-j+1))
                                break
        # tokens
        start, end = 0, 0
        obj_flag = 0
        for token in buffer.split(' '):
            end = start + len(token)
            start_index = '%s.%d' % (cline, start)
            end_index = '%s.%d' % (cline, end)
            if obj_flag:
                self.tag_add('obj', start_index, end_index)
                obj_flag = 0
            # keywords
            if token.strip() in self.highlighter.keywords:
                self.tag_add('kw', start_index, end_index)
                if token.strip() in ['def','class']:
                    obj_flag = 1
            else:
                # numbers
                try:
                    float(token)
                except ValueError:
                    pass
                else:
                    self.tag_add('number', '%s.%d' % (cline, start), "%s.%d" % (cline, end))
            start += len(token)+1

    def insert(self, index, text, *args):
        line = int(self.index(index).split(".")[0])
        Text.insert(self, index, text, *args)
        for i in range(text.count("\n")):
            self.colorize(str(line+i))

class FilePickEdit(Frame):
    def reloadFile(self):
        self.editor.delete("1.0", END)
        filename = self.picked_name.get()
        if os.path.exists(filename):
            new_text = file(filename).read()
            if new_text.strip() == "":
                new_text = "// %s is empty\n" % filename;
            new_text = new_text.replace("\r", "")
        else:
            new_text = ""
        self.editor.insert(INSERT, new_text)

    def onSelChange(self, name, index=0, mode=0):
        self.reloadFile()
        filename = self.picked_name.get()
        self.save_name.set(filename)
        self.save_edit.configure(state=DISABLED)
        self.unmodified = True
        if self.user_onChange != None:
            self.user_onChange(filename)

    def onSaveChange(self, name, index, mode):
        if self.user_onChange != None:
            self.user_onChange(self.save_name.get())

    def autoRename(self):
        # modify "save as" name
        filename = self.picked_name.get()
        if filename == "": filename = "new" + self.file_extension # if no file selected, create new filename
        ext = ""
        extpos = filename.rfind(".")
        if extpos != -1: ext = filename[extpos:]
        base = filename[:extpos]
        hpos = base.rfind("-")
        num = 0
        if hpos != -1:
            try:
                num = int(base[hpos+1:])
                base = base[:hpos]
            except:
                pass
        while True:
            num += 1
            filename = "%s-%d%s" % (base, num, ext)
            if not os.path.exists(filename):
                break
        self.save_name.set(filename)
        # user callback
        if self.user_onChange != None:
            self.user_onChange(filename)

    def onEdit(self):
        if self.unmodified == True:
            self.unmodified = False
            # do auto rename if it's enabled or there is no file selected (editing new file)
            if self.rename_on_edit.get() == 1 or self.picked_name.get() == "":
                self.autoRename()
            # enable editing of save as name
            self.save_edit.configure(state=NORMAL)

    def onChangeRename(self):
        # called when clicking on "rename on edit" checkbox
        if self.rename_on_edit.get() == 1:
            if (not self.unmodified) and self.save_name.get() == self.picked_name.get():
                self.autoRename()
        else:
            self.save_name.set(self.picked_name.get())

    def __init__(self, master, file_mask, default_file, edit_height = None, user_onChange = None, rename_on_edit=0, font = None, coloring=True, allowNone=False, highlighter=None):
        '''
            file_mask: file mask (e.g. "*.foo") or list of file masks (e.g. ["*.foo", "*.abl"])
        '''
        self.master = master
        self.user_onChange = user_onChange
        Frame.__init__(self, master)
        row = 0
        self.unmodified = True
        self.allowNone = allowNone
        self.file_extension = ""
        if type(file_mask) != list:
            file_mask = [file_mask]
        if "." in file_mask[0]:
            self.file_extension = file_mask[0][file_mask[0].rfind('.'):]
        # read filenames
        self.file_mask = file_mask
        self.updateList()
        # filename frame
        self.list_frame = Frame(self)
        self.list_frame.grid(row=row, column=0, sticky="WE")
        self.list_frame.columnconfigure(0, weight=1)
        # create list
        self.picked_name = StringVar(self)
        self.makelist()
        # save button
        self.save_button = Button(self.list_frame, text="save", command=self.save, height=1)
        self.save_button.grid(row=0, column=1, sticky="E")
        # editor
        row += 1
        if coloring:
            self.editor = SyntaxHighlightingText(self, self.onEdit, highlighter=highlighter)
        else:
            self.editor = ScrolledText2(self, self.onEdit)
        if font != None:
            self.editor.configure(font=font)
        if edit_height is not None:
            self.editor.configure(height=edit_height)
        self.editor.grid(row=row, column=0, sticky="NEWS")
        self.rowconfigure(row, weight=1)
        self.columnconfigure(0, weight=1)
        # option to change filename on edit
        row += 1
        self.options_frame = Frame(self)
        self.options_frame.grid(row=row, column=0, sticky=W)
        self.rename_on_edit = IntVar()
        cb = Checkbutton(self.options_frame, text="rename on edit", variable=self.rename_on_edit)
        cb.pack(side=LEFT)
        cb.configure(command=self.onChangeRename)
        self.rename_on_edit.set(rename_on_edit)
        # filename frame
        row += 1
        self.filename_frame = Frame(self)
        self.filename_frame.grid(row=row, column=0, sticky="WE")
        self.filename_frame.columnconfigure(0, weight=1)
        # save as filename
        self.save_name = StringVar(self)
        self.save_edit = Entry(self.filename_frame, textvariable = self.save_name)
        self.save_edit.grid(row=0, column=0, sticky="WE")
        self.save_name.trace("w", self.onSaveChange)
        # pick default if applicable
        self.select(default_file)
        self.row = row

    def updateList(self):
        self.files = []
        if self.allowNone:
            self.files.append("")
        for filename in os.listdir("."):
            for fm in self.file_mask:
                if fnmatch(filename, fm):
                    self.files.append(filename)
        self.files.sort()
        if len(self.files) == 0 and not self.allowNone: self.files.append("(no %s files found)" % str(self.file_mask    ))

    def select(self, filename):
        ''' selects the item given by filename '''
        if filename in self.files:
            if not havePMW:
                self.picked_name.set(filename)
            else:
                self.list.selectitem(self.files.index(filename))
                self.onSelChange(filename)

    def makelist(self):
        if havePMW:
            self.list = Pmw.ComboBox(self.list_frame,
                    selectioncommand = self.onSelChange,
                    scrolledlist_items = self.files,
            )
            self.list.grid(row=0, column=0, padx=0, pady=0, sticky="NEWS")
            self.list.component('entryfield').component('entry').configure(state = 'readonly', relief = 'raised')
            self.picked_name = self.list
        else:
            self.list = apply(OptionMenu, (self.list_frame, self.picked_name) + tuple(self.files))
            self.list.grid(row=0, column=0, sticky="NEW")
            self.picked_name.trace("w", self.onSelChange)

    def save(self):
        self.get()

    def set(self, selected_item):
        self.select(selected_item)

    def get(self):
        ''' gets the name of the currently selected file, saving it first if necessary '''
        filename = self.save_name.get()
        if self.unmodified == False:
            self.unmodified = True
            # save the file
            f = file(filename, "w")
            f.write(self.editor.get("1.0", END))
            f.close()
            # add it to the list of files
            if not filename in self.files:
                self.files.append(filename)
                self.files.sort()
                self.list.destroy()
                self.makelist()
            # set it as the new pick
            #if havePMW:
            #    self.picked_name.selectitem(self.files.index(filename), 1)
            #else:
            #    self.picked_name.set(filename)
            self.select(filename)
            self.save_edit.configure(state=DISABLED)
        return filename

    def get_text(self):
        return self.editor.get("1.0", END)

    def get_filename(self):
        return self.save_name.get()


class FilePick(Frame):
    def __init__(self, master, file_mask, default_file, user_onChange = None, font = None, dirs = (".", ), allowNone = False):
        ''' file_mask: file mask or list of file masks '''
        self.master = master
        self.user_onChange = user_onChange
        Frame.__init__(self, master)
        self.columnconfigure(0, weight=1)
        self.unmodified = True
        self.file_extension = ""
        if "." in file_mask:
            self.file_extension = file_mask[file_mask.rfind('.'):]
        if type(file_mask) != list:
            file_mask = [file_mask]
        self.file_masks = file_mask
        self.allowNone = allowNone
        self.dirs = dirs
        # create list of files
        self.updateList()
        # pick default if applicable
        self.set(default_file)

    def onSelChange(self, name, index=0, mode=0):
        filename = self.picked_name.get()
        if self.user_onChange != None:
            self.user_onChange(filename)

    def updateList(self):
        prev_sel = self.get()
        # get list of files (paths)
        self.files = []
        if self.allowNone:
            self.files.append("")
        for fm in self.file_masks:
            for dir in self.dirs:
                try:
                    for filename in os.listdir(dir):
                        if fnmatch(filename, fm):
                            if dir != ".":
                                path = os.path.join(dir, filename)
                            else:
                                path = filename
                            self.files.append(path)
                except:
                    pass
        self.files.sort()
        if len(self.files) == 0: self.files.append("(no %s files found)" %  self.file_masks)
        # create list object
        self._makelist()
        # reselect
        self.set(prev_sel)

    def getList(self):
        ''' returns the current list of files '''
        return self.files

    def _makelist(self):
        if havePMW:
            self.list = Pmw.ComboBox(self,
                    selectioncommand = self.onSelChange,
                    scrolledlist_items = self.files,
            )
            self.list.grid(row=0, column=0, padx=0, sticky="NEWS")
            self.list.component('entryfield').component('entry').configure(state = 'readonly', relief = 'raised')
            self.picked_name = self.list
        else:
            self.picked_name = StringVar(self)
            self.list = apply(OptionMenu, (self, self.picked_name) + tuple(self.files))
            self.list.grid(row=0, column=0, sticky="NEW")
            self.picked_name.trace("w", self.onSelChange)

    def set(self, filename):
        default_file = filename
        if default_file in self.files:
            if not havePMW:
                self.picked_name.set(default_file) # default value
            else:
                self.list.selectitem(self.files.index(default_file))
                self.onSelChange(default_file)
                pass

    def get(self):
        if not hasattr(self, 'picked_name'):
            return None
        return self.picked_name.get()

class DropdownList:
    def __init__(self, master, items, default=None, allowNone=False, onSelChange=None):
        if allowNone:
            items = tuple([""] + list(items))
        self.items = items
        if havePMW:
            self.list = Pmw.ComboBox(master, selectioncommand = onSelChange, scrolledlist_items = items)
            self.list.component('entryfield').component('entry').configure(state = 'readonly', relief = 'raised')
            self.picked_name = self.list
        else:
            self.picked_name = StringVar(master)
            self.list = apply(OptionMenu, (master, self.picked_name) + tuple(items))
            if onSelChange is not None:
                self.picked_name.trace("w", onSelChange)
        if default is not None:
            self.set(default)
        else:
            self.set(self.items[0])

    def __getattr__(self, name):
        return getattr(self.list, name)

    def get(self):
        return self.picked_name.get()

    def set(self, item):
        if item in self.items:
            if not havePMW:
                self.picked_name.set(item)
            else:
                self.list.selectitem(item)
                #self.onSelChange(default_file)

class Checkbox(Checkbutton):
    def __init__(self, master, text, default=None, **args):
        self.var = IntVar()
        Checkbutton.__init__(self, master, text=text, variable=self.var, **args)
        if default is not None:
            self.var.set(default)

    def get(self):
        return self.var.get()
