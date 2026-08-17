package com.ghostmode.app.shell;

interface IUserService {
    String runCommand(String command) = 1;
    void destroy() = 16777114;
}
