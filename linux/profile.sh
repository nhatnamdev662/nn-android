if [ -d "$HOME/.buddy" ] && [ ! -d "$HOME/.nn" ]; then
  mv "$HOME/.buddy" "$HOME/.nn" 2>/dev/null
fi
[ -d "$HOME/.nn" ] && [ ! -e "$HOME/.buddy" ] && ln -s "$HOME/.nn" "$HOME/.buddy" 2>/dev/null

# Buddy's shell setup (installed as /etc/profile.d/buddy.sh by the app; changes are overwritten on update)
# Alpine's /etc/profile resets PATH: keep Claude Code (~/.local/bin) and Buddy's tools reachable.
export PATH="$HOME/.local/bin:$HOME/bin:$PATH"
export EDITOR="${EDITOR:-vi}"
alias ls='ls --color=auto'
alias grep='grep --color=auto'
# a compact, colourful prompt: nn ~/folder ❯
if [ -n "$BASH_VERSION" ]; then
  PS1='\[\e[1;38;5;209m\]nn\[\e[0m\] \[\e[38;5;110m\]\w\[\e[0m\] \[\e[38;5;245m\]❯\[\e[0m\] '
else
  PS1='nn $PWD > '
fi
