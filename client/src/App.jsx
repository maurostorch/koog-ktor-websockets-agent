import React, {useEffect, useRef, useState} from 'react';
import {ThemeProvider, createTheme} from '@mui/material/styles';
import {Box, Paper, Typography, TextField, Button} from '@mui/material';
import ReactMarkdown from 'react-markdown';

const darkGreenTheme = createTheme({
    palette: {
        mode: 'dark',
        background: {
            default: '#102820',
            paper: '#184d36',
        },
        primary: {
            main: '#21c47b',
            contrastText: '#fff',
        },
        text: {
            primary: '#e0ffe6',
            secondary: '#a5d6b5',
        },
    },
});

const getPlaceholderFromPath = () => {
    // e.g. for /chat/room42, returns 'room42'
    const pathParts = window.location.pathname.split('/').filter(Boolean);
    // adjust index as needed; here, last part is used
    return pathParts.length > 1 ? pathParts[pathParts.length - 1] : 'base';
};

const WS_BASE_URL = 'ws://localhost:8080/agent';

export default function App() {
    const [messages, setMessages] = useState([]);
    const [input, setInput] = useState('');
    const wsRef = useRef(null);

    useEffect(() => {
        const placeholder = getPlaceholderFromPath();
        const wsUrl = `${WS_BASE_URL}/base`;
        if (wsRef.current) {
            wsRef.current.close();
        }
        wsRef.current = new window.WebSocket(wsUrl);
        wsRef.current.onopen = () => {
            console.log('Connection opened');
        };
        wsRef.current.onmessage = (e) => {
            console.log(e);
            if (e.data === "__END__") {
                // wsRef.current.close();
                // wsRef.current = null;
                return;
            } else {
                setMessages((msgs) => [...msgs, {type: "assistant", content: e.data}]);
            }
        };
        wsRef.current.onerror = (err) => {
            console.log(err);
            // setMessages((msgs) => [...msgs, "Connection error"]);
            // wsRef.current.close();
            // wsRef.current = null;
        };
        return () => {
            if (wsRef.current) {
                wsRef.current.close();
            }
        };
    }, []);

    const handleSend = async (e) => {
        e.preventDefault();
        console.log(wsRef.current);
        if (wsRef.current && wsRef.current.readyState === WebSocket.OPEN && input.trim()) {
            wsRef.current.send(input);
            setMessages((msgs) => [...msgs, {type: "user", content: input}]);
            setInput('');
        }
    };

    return (
        <ThemeProvider theme={darkGreenTheme}>
            <Box
                sx={{
                    minHeight: '100vh',
                    bgcolor: 'background.default',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                }}
            >
                <Paper
                    elevation={8}
                    sx={{
                        width: 600,
                        height: '100vh',
                        display: 'flex',
                        flexDirection: 'column',
                        bgcolor: 'background.paper',
                        borderRadius: 3,
                        boxShadow: 8,
                        p: 0,
                    }}
                >
                    <Box sx={{p: 3, borderBottom: '1px solid #295c44'}}>
                        <Typography variant="h5" fontWeight={700} color="primary.contrastText" align="center">
                            Agent
                        </Typography>
                    </Box>
                    <Box
                        sx={{
                            flex: 1,
                            overflowY: 'auto',
                            p: 2,
                            display: 'flex',
                            flexDirection: 'column',
                            gap: 1,
                        }}
                    >
                        {messages.map((msg, idx) => (
                            <Box
                                key={idx}
                                sx={{
                                    bgcolor: msg.type === "assistant" ? 'primary.main' : 'background.default',
                                    color: msg.type === "assistant" ? 'primary.contrastText' : 'text.primary',
                                    px: 2,
                                    py: 1,
                                    borderRadius: 2,
                                    alignSelf: msg.type === "assistant" ? 'flex-end' : 'flex-start',
                                    maxWidth: '80%',
                                    wordBreak: 'break-word',
                                }}
                            >
                                {msg.type === "assistant" ? "Assistant":"You"}<ReactMarkdown>{msg.content}</ReactMarkdown>
                            </Box>
                        ))}
                    </Box>
                    <Box
                        component="form"
                        onSubmit={handleSend}
                        sx={{
                            p: 2,
                            borderTop: '1px solid #295c44',
                            bgcolor: 'background.paper',
                            display: 'flex',
                            gap: 1,
                        }}
                    >
                        <TextField
                            value={input}
                            onChange={e => setInput(e.target.value)}
                            placeholder="Type a message..."
                            variant="outlined"
                            size="small"
                            fullWidth
                            sx={{
                                input: {color: 'text.primary'},
                                bgcolor: 'background.default',
                                borderRadius: 1,
                            }}
                        />
                        <Button
                            type="submit"
                            variant="contained"
                            color="primary"
                            sx={{minWidth: 80, fontWeight: 700}}
                        >
                            Send
                        </Button>
                    </Box>
                </Paper>
            </Box>
        </ThemeProvider>
    );
}
